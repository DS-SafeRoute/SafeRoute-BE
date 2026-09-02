package com.saferoute.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.config.JpaAuditingConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class BuildingDeletionIntegrationTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private TrainingScenarioRepository trainingScenarioRepository;

    @Autowired
    private MapNodeJpaRepository mapNodeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deletesBuildingWithFloorAndDrawingDataWhenTrainingHistoryDoesNotExist() {
        userRepository.save(User.create(
                "manager", "encoded-password", EMAIL, UserRole.MANAGER, SCHOOL_NAME));
        Building building = buildingRepository.save(Building.create(
                "공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = floorRepository.save(Floor.create(building, 1));
        MapNode node = mapNodeRepository.save(MapNode.create(
                floor, "EXIT_001", NodeType.EXIT, "정문 출구", 0.5, 0.5, true));
        UUID buildingId = building.getId();
        UUID floorId = floor.getId();
        UUID nodeId = node.getId();
        entityManager.flush();
        entityManager.clear();

        BuildingService service = new BuildingService(
                buildingRepository,
                userRepository,
                floorRepository,
                trainingScenarioRepository);
        service.deleteBuilding(buildingId, EMAIL);
        entityManager.flush();
        entityManager.clear();

        assertThat(buildingRepository.findById(buildingId)).isEmpty();
        assertThat(floorRepository.findById(floorId)).isEmpty();
        assertThat(mapNodeRepository.findById(nodeId)).isEmpty();
    }
}
