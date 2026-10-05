package com.saferoute.domain.evacuation.recalculation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

// triggerAsync()가 self-invocation으로 trigger()를 호출하는 구조라, @Async 프록시를 통해
// 실제로 다른 스레드에서 실행됐을 때도 trigger() 내부의 비관적 락 쿼리/JPA 쓰기가 "트랜잭션
// 없음" 오류 없이 끝까지 커밋되는지를 목이 아닌 진짜 스레드/트랜잭션 경계로 검증한다
// (triggerForFireSpread()에 REQUIRES_NEW가 필요했던 것과 같은 종류의 함정, #250).
@SpringBootTest
class RouteRecalculationServiceAsyncIntegrationTest {

    private static final String SCHOOL_NAME = "비동기 재탐색 테스트교";

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
    private RouteRecalculationRepository routeRecalculationRepository;
    @Autowired
    private RouteRecalculationService routeRecalculationService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void triggerAsync_persistsPendingRecalculationWithoutThrowing() {
        Fixture fixture = transactionTemplate.execute(status -> createFixture());

        // 호출 자체가 블로킹 없이 즉시 반환되는지(동기 trigger()와 달리 호출자가 기다리지
        // 않는지)는 호출 스레드에서 예외가 전혀 전파되지 않는다는 사실로도 간접 확인된다 -
        // self-invocation 트랜잭션 버그가 있었다면 비동기 스레드에서 예외가 나고,
        // AsyncConfig의 핸들러가 삼켜서 호출자는 멀쩡한데 PENDING은 영원히 안 생겼을 것이다.
        routeRecalculationService.triggerAsync(
                fixture.session(), fixture.congestedCorridor(), CongestionLevel.CROWDED,
                RecalculationTriggerType.LEVEL_UP, "CCTV_001", 3.5);

        RouteRecalculation pending = awaitPendingRecalculation(fixture.session().getId());

        assertThat(pending.getTriggerType()).isEqualTo(RecalculationTriggerType.LEVEL_UP);
        assertThat(pending.getCongestionLevel()).isEqualTo(CongestionLevel.CROWDED);
        assertThat(pending.getCctvCode()).isEqualTo("CCTV_001");
        // recalculatedNodeIds는 @ElementCollection(지연 로딩)이라 세션이 열려 있을 때 읽어야
        // 한다 - 왼쪽(혼잡) 코너를 피해 오른쪽 코너를 새 후보로 골랐는지까지 확인한다.
        List<UUID> recalculatedNodeIds = transactionTemplate.execute(status -> List.copyOf(
                routeRecalculationRepository.findById(pending.getId()).orElseThrow().getRecalculatedNodeIds()));
        assertThat(recalculatedNodeIds).contains(fixture.midRight().getId());
    }

    private RouteRecalculation awaitPendingRecalculation(UUID sessionId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        while (Instant.now().isBefore(deadline)) {
            Optional<RouteRecalculation> found = routeRecalculationRepository
                    .findAllByTrainingSession_IdAndStatus(sessionId, RecalculationStatus.PENDING)
                    .stream()
                    .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            sleepQuietly();
        }
        throw new AssertionError("triggerAsync()가 제한 시간 내에 PENDING 재탐색을 만들지 못했습니다");
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    // 왼쪽 코너(짧음, start-midLeft-exit)와 오른쪽 코너(길지만 덜 혼잡, start-midRight-exit)
    // 두 경로를 만들어, 왼쪽이 혼잡(CROWDED, 3배 가중치)해지면 재탐색이 실제로 오른쪽을
    // 새 후보로 고르는지까지 확인한다 - 경로가 하나뿐이면 가중치를 올려도 후보가 이전과
    // 같아져(trigger()가 "달라진 게 없다"며 PENDING을 만들지 않음) 비동기 동작 자체를
    // 검증할 수 없다.
    private Fixture createFixture() {
        Building building = buildingRepository.save(Building.create(
                "비동기 재탐색 테스트 건물", "부산광역시 해운대구 우동 1-1", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = Floor.create(building, 1);
        floor.upload(3.0, 4.0, "floors/async-recalculation.png");
        floorRepository.save(floor);

        MapNode startNode = mapNodeRepository.save(MapNode.create(
                floor, "START", NodeType.HALLWAY, "출발", 0.1, 0.5, false));
        MapNode midLeft = mapNodeRepository.save(MapNode.create(
                floor, "MID_LEFT", NodeType.HALLWAY, "왼쪽 코너", 0.5, 0.2, false));
        MapNode midRight = mapNodeRepository.save(MapNode.create(
                floor, "MID_RIGHT", NodeType.HALLWAY, "오른쪽 코너", 0.5, 0.8, false));
        MapNode exitNode = mapNodeRepository.save(MapNode.create(
                floor, "EXIT", NodeType.EXIT, "출구", 0.9, 0.5, true));

        MapEdge leftIn = mapEdgeRepository.save(MapEdge.create(floor, startNode, midLeft, 1.0, true));
        MapEdge leftOut = mapEdgeRepository.save(MapEdge.create(floor, midLeft, exitNode, 1.0, true));
        mapEdgeRepository.save(MapEdge.create(floor, startNode, midRight, 1.5, true));
        mapEdgeRepository.save(MapEdge.create(floor, midRight, exitNode, 1.5, true));

        User admin = userRepository.save(User.create(
                "async-recalc-admin", "password", "async-recalc-admin@saferoute.com",
                UserRole.MANAGER, SCHOOL_NAME));

        TrainingScenario scenario = trainingScenarioRepository.save(TrainingScenario.create(
                "비동기 재탐색 테스트 시나리오", 10, Instant.now(), false, FireSpreadSpeed.FAST,
                building, admin, startNode));

        TrainingSession session = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now().minusSeconds(30), admin, scenario);
        trainingSessionRepository.save(session);

        return new Fixture(session, List.of(leftIn, leftOut), midRight);
    }

    private record Fixture(TrainingSession session, List<MapEdge> congestedCorridor, MapNode midRight) {
    }
}
