package com.saferoute.domain.training.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.config.JpaAuditingConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

// 건물당 동시 RUNNING 세션 1개는 DB로 강제되지 않으므로(TrainingSessionRepository 참고), 서로 다른
// 시나리오가 같은 물리 그리드 셀을 화재구역으로 참조할 수 있다. resetFiredCellsByScenarioId가 한
// 시나리오를 리셋할 때 그 셀을 다른 RUNNING 시나리오가 여전히 쓰고 있으면 꺼트리지 않는지 검증한다.
@DataJpaTest
@Import(JpaAuditingConfig.class)
class FireZoneRepositoryTest {

    @Autowired
    private FireZoneRepository fireZoneRepository;

    @Autowired
    private FloorGridCellRepository gridCellRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private TrainingScenarioRepository scenarioRepository;

    @Autowired
    private TrainingSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User admin;
    private Building building;
    private Floor floor;

    private void setUpBuilding() {
        admin = userRepository.save(User.create("admin", "password123!", "admin@saferoute.com", UserRole.MANAGER, "SafeRoute School"));
        building = buildingRepository.save(Building.create("테스트관", "서울특별시 안전구 1", BuildingType.CLASSROOM, "SafeRoute School"));
        floor = floorRepository.save(Floor.create(building, 1));
    }

    private TrainingScenario scenario(String name) {
        return scenarioRepository.save(TrainingScenario.create(
                name, 10, 300, Instant.now(), false, FireSpreadSpeed.MEDIUM, building, admin, null));
    }

    private void sessionWithStatus(TrainingScenario scenario, TrainingStatus status) {
        TrainingSession session = TrainingSession.create(status, Instant.now(), admin, scenario);
        sessionRepository.save(session);
    }

    private FloorGridCell cell(int row, int col) {
        FloorGridCell created = gridCellRepository.save(FloorGridCell.create(floor, row, col, true, 0.0, 0.0));
        created.markFired();
        return created;
    }

    @Test
    @DisplayName("다른 시나리오가 RUNNING으로 여전히 쓰는 셀은 리셋 대상에서 제외되고, 그 시나리오만 쓰는 셀은 리셋된다")
    void resetFiredCellsByScenarioId_preservesCellStillUsedByAnotherRunningScenario() {
        setUpBuilding();
        TrainingScenario scenarioA = scenario("시나리오A");
        TrainingScenario scenarioB = scenario("시나리오B");
        sessionWithStatus(scenarioA, TrainingStatus.RUNNING);
        sessionWithStatus(scenarioB, TrainingStatus.RUNNING);

        FloorGridCell sharedCell = cell(0, 0);
        FloorGridCell onlyACell = cell(0, 1);
        fireZoneRepository.save(FireZone.createOrigin(scenarioA, floor, sharedCell));
        fireZoneRepository.save(FireZone.createOrigin(scenarioB, floor, sharedCell));
        fireZoneRepository.save(FireZone.createOrigin(scenarioA, floor, onlyACell));
        entityManager.flush();

        // 시나리오A가 종료되어 리셋된다. 시나리오B는 여전히 RUNNING이라 sharedCell은 꺼지면 안 된다.
        fireZoneRepository.resetFiredCellsByScenarioId(scenarioA.getId());
        entityManager.clear();

        FloorGridCell reloadedShared = gridCellRepository.findById(sharedCell.getId()).orElseThrow();
        FloorGridCell reloadedOnlyA = gridCellRepository.findById(onlyACell.getId()).orElseThrow();
        assertThat(reloadedShared.isFired()).isTrue();
        assertThat(reloadedOnlyA.isFired()).isFalse();
    }

    @Test
    @DisplayName("공유하던 다른 시나리오가 더 이상 RUNNING이 아니면 마지막 참조인 시나리오 종료 시 셀이 꺼진다")
    void resetFiredCellsByScenarioId_clearsSharedCellWhenNoOtherRunningReference() {
        setUpBuilding();
        TrainingScenario scenarioA = scenario("시나리오A");
        TrainingScenario scenarioB = scenario("시나리오B");
        sessionWithStatus(scenarioA, TrainingStatus.RUNNING);
        sessionWithStatus(scenarioB, TrainingStatus.COMPLETED); // 더 이상 RUNNING 아님

        FloorGridCell sharedCell = cell(0, 0);
        fireZoneRepository.save(FireZone.createOrigin(scenarioA, floor, sharedCell));
        fireZoneRepository.save(FireZone.createOrigin(scenarioB, floor, sharedCell));
        entityManager.flush();

        fireZoneRepository.resetFiredCellsByScenarioId(scenarioA.getId());
        entityManager.clear();

        FloorGridCell reloaded = gridCellRepository.findById(sharedCell.getId()).orElseThrow();
        assertThat(reloaded.isFired()).isFalse();
    }
}
