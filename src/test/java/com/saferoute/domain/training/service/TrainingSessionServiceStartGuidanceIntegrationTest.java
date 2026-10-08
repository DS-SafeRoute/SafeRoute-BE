package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.entity.LightCommand;
import com.saferoute.domain.device.entity.LightCommandStatus;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.device.repository.LightCommandJpaRepository;
import com.saferoute.domain.device.service.IoTLightDirectionStore;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.telemetry.dynamo.repository.LightDirectionEventRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

// start()가 최초 경로 위 유도등만이 아니라 그 층 전체 유도등을 "출구로 가는 다음 노드" 방향으로
// 실제 DB/트랜잭션 위에서 지정하는지 검증한다 (#260). 예전에는 경로 밖 유도등이 방향이 정해지지 않은
// 채(직전 상태 그대로) 남았다.
//
// 그래프(같은 층, 모두 양방향):
//
//        A ---(1)--- X(EXIT)
//        |           |
//       (1)         (2)
//        |           |
//   START(S) --(2)-- B
//
// 발화점이 S-A 구간에 걸려 있어 최초 경로는 S -> B -> X다. 이 경로 밖에 있는 A의 유도등은
// 화재 구간(S-A)을 피해 곧바로 출구(A -> X)로 향해야 한다.
@SpringBootTest
class TrainingSessionServiceStartGuidanceIntegrationTest {

    private static final String SCHOOL_NAME = "시작 유도등 테스트교";

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
    private MapNodeJpaRepository mapNodeRepository;
    @Autowired
    private MapEdgeJpaRepository mapEdgeRepository;
    @Autowired
    private FloorGridCellRepository floorGridCellRepository;
    @Autowired
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;
    @Autowired
    private FireZoneRepository fireZoneRepository;
    @Autowired
    private CctvJpaRepository cctvRepository;
    @Autowired
    private IoTLightJpaRepository iotLightRepository;
    @Autowired
    private LightCommandJpaRepository lightCommandRepository;
    @Autowired
    private IoTLightDirectionStore directionStore;
    @Autowired
    private TrainingSessionService trainingSessionService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @MockitoSpyBean
    private EvacuationRouteService evacuationRouteService;
    // 유도등 방향 전환 이력(DynamoDB) 기록은 이 테스트의 관심사가 아니다.
    @MockitoBean
    private LightDirectionEventRepository lightDirectionEventRepository;

    @Test
    void start_guidesEveryGuidedLightOnFloorTowardExit() {
        Fixture fixture = transactionTemplate.execute(status -> createFixture());

        trainingSessionService.start(fixture.sessionId(), fixture.managerEmail());

        assertThat(trainingSessionRepository.findById(fixture.sessionId()).orElseThrow().getStatus())
                .isEqualTo(TrainingStatus.RUNNING);
        // 최초 경로(S -> B -> X) 위 유도등은 경로를 따른다.
        assertThat(directionStore.get(fixture.lightS())).isEqualTo(IoTLightDirection.RIGHT);
        assertThat(directionStore.get(fixture.lightB())).isEqualTo(IoTLightDirection.RIGHT);
        // 이번 버그의 핵심: 경로 밖(A)이라 예전에는 방향이 정해지지 않던 유도등이
        // 화재 구간(S-A)을 피해 출구로 가는 다음 홉(X 쪽, RIGHT)으로 지정된다.
        assertThat(directionStore.get(fixture.lightA())).isEqualTo(IoTLightDirection.RIGHT);
        assertThat(pendingCommands(fixture.lightA())).singleElement()
                .extracting(LightCommand::getDirection).isEqualTo(IoTLightDirection.RIGHT);
        // 출구 노드 자체의 유도등은 다음 홉이 없어 평상시(BOTH)로 둔다.
        assertThat(directionStore.get(fixture.lightX())).isEqualTo(IoTLightDirection.BOTH);
        // 다른 층 유도등은 건드리지 않는다.
        assertThat(directionStore.get(fixture.lightOtherFloor())).isEqualTo(IoTLightDirection.OFF);
        assertThat(pendingCommands(fixture.lightOtherFloor())).isEmpty();
    }

    @Test
    void start_whenNextHopComputationFails_stillStartsAndAppliesPathGuidance() {
        Fixture fixture = transactionTemplate.execute(status -> createFixture());
        doThrow(new IllegalStateException("next hop failure"))
                .when(evacuationRouteService).computeNextHops(any(), any(), any());

        trainingSessionService.start(fixture.sessionId(), fixture.managerEmail());

        // 다음 홉은 부가 정보라 계산이 터져도 훈련 시작 자체는 롤백되지 않고 커밋돼 있어야 한다.
        assertThat(trainingSessionRepository.findById(fixture.sessionId()).orElseThrow().getStatus())
                .isEqualTo(TrainingStatus.RUNNING);
        // 최초 경로 위 유도등은 그대로 반영되고, 경로 밖 유도등은 BOTH가 된다.
        assertThat(directionStore.get(fixture.lightS())).isEqualTo(IoTLightDirection.RIGHT);
        assertThat(directionStore.get(fixture.lightB())).isEqualTo(IoTLightDirection.RIGHT);
        assertThat(directionStore.get(fixture.lightA())).isEqualTo(IoTLightDirection.BOTH);
    }

    private List<LightCommand> pendingCommands(UUID lightId) {
        return lightCommandRepository.findAllByLight_IdAndStatus(lightId, LightCommandStatus.PENDING);
    }

    private Fixture createFixture() {
        // 테스트 메서드마다 같은 인메모리 DB에 픽스처를 만들므로 유니크 제약이 걸린 값에 접미사를 붙인다.
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Building building = buildingRepository.save(Building.create(
                "시작유도등 " + suffix, "부산광역시 해운대구 우동 8-8", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = floorRepository.save(newFloor(building, 1, "start-guidance-1-" + suffix));
        Floor otherFloor = floorRepository.save(newFloor(building, 2, "start-guidance-2-" + suffix));

        MapNode start = mapNodeRepository.save(MapNode.create(floor, "S", NodeType.START, "출발", 0.1, 0.5, false));
        MapNode nodeA = mapNodeRepository.save(MapNode.create(floor, "A", NodeType.HALLWAY, "A", 0.1, 0.1, false));
        MapNode nodeB = mapNodeRepository.save(MapNode.create(floor, "B", NodeType.HALLWAY, "B", 0.9, 0.9, false));
        MapNode exit = mapNodeRepository.save(MapNode.create(floor, "X", NodeType.EXIT, "출구", 0.9, 0.1, true));

        MapEdge startA = mapEdgeRepository.save(MapEdge.create(floor, start, nodeA, 1.0, true));
        MapEdge aExit = mapEdgeRepository.save(MapEdge.create(floor, nodeA, exit, 1.0, true));
        MapEdge startB = mapEdgeRepository.save(MapEdge.create(floor, start, nodeB, 2.0, true));
        MapEdge bExit = mapEdgeRepository.save(MapEdge.create(floor, nodeB, exit, 2.0, true));

        // 발화점 셀이 S-A 구간을 덮는다. start()가 이 셀을 markFired()한 뒤 그 구간을 경로/다음 홉에서 제외한다.
        FloorGridCell fireCell = floorGridCellRepository.save(FloorGridCell.create(floor, 0, 0, true, 0.1, 0.3));
        mapEdgeGridCellRepository.save(MapEdgeGridCell.create(startA, fireCell));

        // 다른 층: 이 층 유도등은 시작(시작 노드 층 기준)에 영향받지 않아야 한다.
        MapNode otherDecision = mapNodeRepository.save(
                MapNode.create(otherFloor, "D2", NodeType.HALLWAY, "다른 층 분기", 0.5, 0.5, false));
        MapNode otherLeft = mapNodeRepository.save(
                MapNode.create(otherFloor, "L2", NodeType.HALLWAY, "다른 층 왼쪽", 0.1, 0.5, false));
        MapNode otherRight = mapNodeRepository.save(
                MapNode.create(otherFloor, "R2", NodeType.HALLWAY, "다른 층 오른쪽", 0.9, 0.5, false));
        MapEdge otherLeftEdge = mapEdgeRepository.save(MapEdge.create(otherFloor, otherDecision, otherLeft, 1.0, true));
        MapEdge otherRightEdge = mapEdgeRepository.save(MapEdge.create(otherFloor, otherDecision, otherRight, 1.0, true));

        Cctv cctv = cctvRepository.save(Cctv.create(
                "CCTV_" + suffix, "시작 유도등 CCTV",
                mapNodeRepository.save(MapNode.createCustom(floor, "CCTV_" + suffix, "CCTV", 0.5, 0.5))));

        IoTLight lightS = saveLight(floor, "LS_" + suffix, cctv, start, startA, startB);
        IoTLight lightA = saveLight(floor, "LA_" + suffix, cctv, nodeA, startA, aExit);
        IoTLight lightB = saveLight(floor, "LB_" + suffix, cctv, nodeB, startB, bExit);
        IoTLight lightX = saveLight(floor, "LX_" + suffix, cctv, exit, aExit, bExit);
        IoTLight lightOther = saveLight(otherFloor, "LO_" + suffix, cctv, otherDecision, otherLeftEdge, otherRightEdge);

        String managerEmail = "start-guidance-" + suffix + "@saferoute.com";
        User manager = userRepository.save(User.create(
                "sg-" + suffix, "password", managerEmail, UserRole.MANAGER, SCHOOL_NAME));
        TrainingScenario scenario = trainingScenarioRepository.save(TrainingScenario.create(
                "시작 유도등 테스트 시나리오", 10, Instant.now(), false, FireSpreadSpeed.FAST, building, manager, start));
        fireZoneRepository.save(FireZone.createOrigin(scenario, floor, fireCell));
        TrainingSession session = trainingSessionRepository.save(TrainingSession.create(
                TrainingStatus.SCHEDULED, Instant.now().plusSeconds(60), manager, scenario));

        return new Fixture(session.getId(), managerEmail, lightS.getId(), lightA.getId(), lightB.getId(),
                lightX.getId(), lightOther.getId());
    }

    private Floor newFloor(Building building, int floorNumber, String imageKey) {
        Floor floor = Floor.create(building, floorNumber);
        floor.upload(3.0, 4.0, "floors/" + imageKey + ".png");
        return floor;
    }

    private IoTLight saveLight(Floor floor, String code, Cctv cctv, MapNode decisionNode,
            MapEdge leftEdge, MapEdge rightEdge) {
        IoTLight light = IoTLight.create(code, code,
                mapNodeRepository.save(MapNode.createCustom(floor, code, code, 0.5, 0.5)));
        light.assignCctv(cctv);
        light.configureGuidance(decisionNode, leftEdge, rightEdge);
        return iotLightRepository.save(light);
    }

    private record Fixture(UUID sessionId, String managerEmail, UUID lightS, UUID lightA, UUID lightB,
            UUID lightX, UUID lightOtherFloor) {
    }
}
