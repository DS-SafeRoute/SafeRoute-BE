package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

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
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// spreadOneStep()과 같은 트랜잭션에서 경로 재탐색을 직접 호출하면, 재탐색이 던지는 예외가
// 화재 확산(셀 발화, FireZone 저장, 세대 증가) 자체를 롤백시킨다 - FireSpreadStepService의
// afterCommit 분리가 실제로 이 문제를 막는지 실제 트랜잭션 경계로 검증한다.
@SpringBootTest
class FireSpreadStepServiceTransactionIsolationIntegrationTest {

    private static final String SCHOOL_NAME = "화재격리 테스트교";

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
    private FireSpreadStepService fireSpreadStepService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private RouteRecalculationService routeRecalculationService;

    @Test
    void recalculationFailure_doesNotRollBackFireSpreadCommit() {
        willThrow(new RuntimeException("경로 재탐색 중 예상치 못한 오류"))
                .given(routeRecalculationService).triggerForFireSpread(any(), any());

        UUID sessionId = transactionTemplate.execute(status -> createRunningSessionWithAdjacentFireCells());

        assertThatCode(() -> fireSpreadStepService.spreadOneStep(sessionId)).doesNotThrowAnyException();

        TrainingSession reloaded = trainingSessionRepository.findById(sessionId).orElseThrow();
        assertThat(reloaded.getCurrentGeneration()).isEqualTo(1);

        List<FireZone> fireZones = fireZoneRepository
                .findByScenario_IdOrderBySpreadGenerationAscAddedAtAsc(reloaded.getScenario().getId());
        assertThat(fireZones).hasSize(2);
        assertThat(fireZones).extracting(FireZone::getSpreadGeneration).containsExactlyInAnyOrder(0, 1);
    }

    private UUID createRunningSessionWithAdjacentFireCells() {
        Building building = buildingRepository.save(Building.create(
                "화재 격리 테스트 건물", "부산광역시 해운대구 우동 123-45", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = Floor.create(building, 1);
        floor.upload(3.0, 4.0, "floors/fire-isolation.png");
        floorRepository.save(floor);

        MapNode fromNode = mapNodeRepository.save(MapNode.create(
                floor, "FROM", NodeType.HALLWAY, "출발", 0.1, 0.5, false));
        MapNode toNode = mapNodeRepository.save(MapNode.create(
                floor, "TO", NodeType.HALLWAY, "도착", 0.9, 0.5, false));
        MapEdge edge = mapEdgeRepository.save(MapEdge.create(floor, fromNode, toNode, 2.0, true));

        FloorGridCell originCell = floorGridCellRepository.save(
                FloorGridCell.create(floor, 0, 0, true, 0.1, 0.5));
        FloorGridCell neighborCell = floorGridCellRepository.save(
                FloorGridCell.create(floor, 0, 1, true, 0.9, 0.5));
        mapEdgeGridCellRepository.save(MapEdgeGridCell.create(edge, neighborCell));

        User admin = userRepository.save(User.create(
                "fire-isolation-admin", "password", "fire-isolation@saferoute.com", UserRole.MANAGER, SCHOOL_NAME));

        TrainingScenario scenario = trainingScenarioRepository.save(TrainingScenario.create(
                "화재 격리 테스트 시나리오", 10, Instant.now(), false, FireSpreadSpeed.FAST, building, admin, fromNode));

        fireZoneRepository.save(FireZone.createOrigin(scenario, floor, originCell));

        TrainingSession session = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now().minusSeconds(30), admin, scenario);
        trainingSessionRepository.save(session);
        return session.getId();
    }
}
