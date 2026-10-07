package com.saferoute.domain.evacuation.recalculation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.recalculation.dto.response.CurrentRouteResponse;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

// Mockito 유닛 테스트는 SCHEDULED 세션에서도 isFired=true인 셀을 목으로 만들어버려서, 실제로는 훈련 시작 전에
// 발화점 셀의 isFired가 false(evacuation-setup은 isFired를 켜지 않는다)라는 사실을 검증하지 못했다(#261).
// 여기서는 실제 DB로 "설정만 저장하고 아직 시작하지 않은" 상태를 그대로 만들어 확인한다.
@SpringBootTest
class RouteRecalculationServiceScheduledCurrentRouteIntegrationTest {

    private static final String SCHOOL_NAME = "시작전경로 테스트교";
    private static final String EMAIL = "scheduled-route@saferoute.com";

    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private FloorRepository floorRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainingScenarioRepository trainingScenarioRepository;
    @Autowired
    private TrainingSessionRepository trainingSessionRepository;
    @Autowired
    private FloorGridCellRepository floorGridCellRepository;
    @Autowired
    private MapNodeJpaRepository mapNodeRepository;
    @Autowired
    private MapEdgeJpaRepository mapEdgeRepository;
    @Autowired
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;
    @Autowired
    private FireZoneRepository fireZoneRepository;
    @Autowired
    private RouteRecalculationService routeRecalculationService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID exitANodeId;
    private UUID exitBNodeId;

    @Test
    void currentRoute_beforeTrainingStart_avoidsConfiguredFireOrigin() {
        UUID sessionId = transactionTemplate.execute(status -> createScheduledSessionWithFireOrigin());

        CurrentRouteResponse response = routeRecalculationService.getCurrentRoute(sessionId, EMAIL);

        // 더 짧은 A쪽 구간에 발화점이 있으므로, 시작 전이라도 그쪽을 피해 B 출구로 가야 한다.
        assertThat(response.source()).isEqualTo(CurrentRouteResponse.RouteSource.INITIAL);
        assertThat(response.path()).extracting(CurrentRouteResponse.NodePoint::nodeId)
                .contains(exitBNodeId)
                .doesNotContain(exitANodeId);
    }

    private UUID createScheduledSessionWithFireOrigin() {
        Building building = buildingRepository.save(Building.create(
                "시작전 경로 테스트 건물", "부산광역시 해운대구 우동 123-45", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = Floor.create(building, 1);
        floor.upload(3.0, 4.0, "floors/scheduled-route.png");
        floorRepository.save(floor);

        MapNode start = mapNodeRepository.save(MapNode.create(
                floor, "START", NodeType.HALLWAY, "출발", 0.5, 0.5, false));
        MapNode exitA = mapNodeRepository.save(MapNode.create(
                floor, "EXIT_A", NodeType.EXIT, "출구A", 0.1, 0.5, true));
        MapNode exitB = mapNodeRepository.save(MapNode.create(
                floor, "EXIT_B", NodeType.EXIT, "출구B", 0.9, 0.5, true));
        exitANodeId = exitA.getId();
        exitBNodeId = exitB.getId();

        MapEdge edgeToA = mapEdgeRepository.save(MapEdge.create(floor, start, exitA, 1.0, true));
        mapEdgeRepository.save(MapEdge.create(floor, start, exitB, 5.0, true));

        // 발화점 셀은 isFired=false인 채로 둔다 - evacuation-setup 직후, 훈련 시작 전의 실제 상태.
        FloorGridCell originCell = floorGridCellRepository.save(
                FloorGridCell.create(floor, 0, 0, true, 0.3, 0.5));
        mapEdgeGridCellRepository.save(MapEdgeGridCell.create(edgeToA, originCell));

        User admin = userRepository.save(User.create(
                "sched-route-admin", "password", EMAIL, UserRole.MANAGER, SCHOOL_NAME));
        TrainingScenario scenario = trainingScenarioRepository.save(TrainingScenario.create(
                "시작전 경로 시나리오", 10, java.time.Instant.now(), false, FireSpreadSpeed.FAST, building, admin, start));
        fireZoneRepository.save(FireZone.createOrigin(scenario, floor, originCell));

        TrainingSession session = trainingSessionRepository.save(TrainingSession.schedule(admin, scenario));
        assertThat(session.getStatus()).isEqualTo(TrainingStatus.SCHEDULED);
        assertThat(originCell.isFired()).isFalse();
        return session.getId();
    }
}
