package com.saferoute.domain.evacuation.recalculation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
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
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

// approve()가 ROUTE_RECALCULATION_CROSSES_FIRE(409)를 던질 때 함께 호출하는 cancel()이,
// 그 예외 때문에 트랜잭션 전체가 롤백되면서 같이 사라지지 않고 실제로 커밋되는지 검증한다.
// ApiException은 RuntimeException이라 @Transactional 기본 규칙상 던지면 전체가 롤백되므로,
// approve()에 noRollbackFor = ApiException.class를 걸어뒀다 - 이 테스트는 그 설정이 실제로
// 효과가 있는지 목이 아닌 진짜 트랜잭션 경계로 확인한다.
@SpringBootTest
class RouteRecalculationServiceApproveFireRejectionIntegrationTest {

    private static final String SCHOOL_NAME = "화재거부 테스트교";
    private static final String APPROVER_EMAIL = "fire-reject-approver@saferoute.com";

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
    private RouteRecalculationRepository routeRecalculationRepository;
    @Autowired
    private RouteRecalculationService routeRecalculationService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void approve_crossingFireEdge_rejectsAndPersistsCancellationDespiteException() {
        UUID recalculationId = transactionTemplate.execute(status -> createFireCrossingPendingRecalculation());

        assertThatThrownBy(() -> routeRecalculationService.approve(recalculationId, APPROVER_EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EvacuationErrorCode.ROUTE_RECALCULATION_CROSSES_FIRE);

        RouteRecalculation reloaded = routeRecalculationRepository.findById(recalculationId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RecalculationStatus.CANCELLED);
        assertThat(reloaded.getCancelReason()).isNotBlank();
    }

    private UUID createFireCrossingPendingRecalculation() {
        Building building = buildingRepository.save(Building.create(
                "화재거부 테스트 건물", "부산광역시 해운대구 우동 123-45", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = Floor.create(building, 1);
        floor.upload(3.0, 4.0, "floors/fire-rejection.png");
        floorRepository.save(floor);

        MapNode fromNode = mapNodeRepository.save(MapNode.create(
                floor, "FROM", NodeType.HALLWAY, "출발", 0.1, 0.5, false));
        MapNode toNode = mapNodeRepository.save(MapNode.create(
                floor, "TO", NodeType.EXIT, "출구", 0.9, 0.5, true));
        MapEdge edge = mapEdgeRepository.save(MapEdge.create(floor, fromNode, toNode, 2.0, true));

        FloorGridCell firedCell = floorGridCellRepository.save(
                FloorGridCell.create(floor, 0, 0, true, 0.5, 0.5));
        firedCell.markFired();
        floorGridCellRepository.save(firedCell);
        mapEdgeGridCellRepository.save(MapEdgeGridCell.create(edge, firedCell));

        User admin = userRepository.save(User.create(
                "fire-reject-admin", "password", "fire-reject-admin@saferoute.com", UserRole.MANAGER, SCHOOL_NAME));
        userRepository.save(User.create(
                "fire-reject-approver", "password", APPROVER_EMAIL, UserRole.MANAGER, SCHOOL_NAME));

        TrainingScenario scenario = trainingScenarioRepository.save(TrainingScenario.create(
                "화재거부 테스트 시나리오", 10, Instant.now(), false, FireSpreadSpeed.FAST, building, admin, fromNode));

        // firedCell이 "이 시나리오"의 화재 구간이라는 FireZone 기록도 함께 남긴다(시나리오 범위
        // 필터를 실제로 통과하는지 검증).
        fireZoneRepository.save(FireZone.createOrigin(scenario, floor, firedCell));

        TrainingSession session = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now().minusSeconds(30), admin, scenario);
        trainingSessionRepository.save(session);

        // 승인하려는 경로가 방금 불난 edge(FROM->TO)를 그대로 지나가도록 recalculatedNodeIds를 구성한다.
        RouteRecalculation recalculation = RouteRecalculation.createPending(
                session, edge, "CCTV_001", RecalculationTriggerType.STARTED, CongestionLevel.CROWDED, 3.5,
                List.of(fromNode.getId()), 5.0, List.of(fromNode.getId(), toNode.getId()), 2.0);
        routeRecalculationRepository.save(recalculation);

        return recalculation.getId();
    }
}
