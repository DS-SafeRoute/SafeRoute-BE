package com.saferoute.domain.grid;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.entity.UserZone;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.NodeGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.UserZoneRepository;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.global.api.exception.ApiException;

@SpringBootTest
@Transactional // 테스트마다 롤백
class FloorGridServiceTest {

    @Autowired
    FloorGridService floorGridService;
    @Autowired
    FloorRepository floorRepository;
    @Autowired
    MapNodeJpaRepository mapNodeRepository;
    @Autowired
    FloorGridCellRepository floorGridCellRepository;
    @Autowired
    UserZoneRepository userZoneRepository;
    @Autowired
    NodeGridCellRepository nodeGridCellRepository;
    @Autowired
    BuildingRepository buildingRepository;

    private Floor floor;

    @BeforeEach
    void setUp() {
        Building building = Building.create("테스트 빌딩", "서울시 테스트구 테스트로 123", BuildingType.CLASSROOM);
        buildingRepository.save(building);

        floor = Floor.create(building, 3);
        floor.upload(30.0, 40.0, "dummy-key"); // realHeight=30, realWidth=40, mapImageKey -> segmentationStatus DONE
        floorRepository.save(floor);
    }

    @Test
    void createGrid_calculatesCorrectRowAndColumnCount() {
        var response = floorGridService.createOrRegenerateGrid(
                floor.getId(), new CreateOrUpdateFloorGridRequest(1.0));

        assertThat(response.rows()).isEqualTo(30);   // ceil(30 / 1.0)
        assertThat(response.columns()).isEqualTo(40); // ceil(40 / 1.0)

        long cellCount = floorGridCellRepository.findAllByFloor_Id(floor.getId()).size();
        assertThat(cellCount).isEqualTo(30 * 40);
    }

    @Test
    void regenerateGrid_deletesCctvNodeButKeepsGuideLightNode() {
        MapNode cctv = MapNode.createCustom(floor, "cctv_1", "복도 CCTV", 0.5, 0.5,
                CustomDeviceType.CCTV);
        MapNode light = MapNode.createCustom(floor, "light_1", "복도 유도등", 0.3, 0.3,
                CustomDeviceType.GUIDE_LIGHT);
        mapNodeRepository.save(cctv);
        mapNodeRepository.save(light);

        floorGridService.createOrRegenerateGrid(floor.getId(), new CreateOrUpdateFloorGridRequest(1.0));

        assertThat(mapNodeRepository.findById(cctv.getId())).isEmpty();
        assertThat(mapNodeRepository.findById(light.getId())).isPresent();
    }

    @Test
    void regenerateGrid_deletesExistingUserZone() {
        UserZone zone = UserZone.create(floor, "3층 앞 복도");
        userZoneRepository.save(zone);

        floorGridService.createOrRegenerateGrid(floor.getId(), new CreateOrUpdateFloorGridRequest(1.0));

        assertThat(userZoneRepository.findAllByFloor_Id(floor.getId())).isEmpty();
    }

    @Test
    void regenerateGrid_remapsSurvivingNodeToNewGrid() {
        MapNode stair = MapNode.create(floor, "stair_1", NodeType.STAIR, "계단", 0.1, 0.1, false);
        mapNodeRepository.save(stair);

        floorGridService.createOrRegenerateGrid(floor.getId(), new CreateOrUpdateFloorGridRequest(1.0));

        var mapping = nodeGridCellRepository.findByNode_Id(stair.getId());
        assertThat(mapping).isPresent();
        // x=0.1 * columns(40) = 4, y=0.1 * rows(30) = 3
        assertThat(mapping.get().getGridCell().getColumnIndex()).isEqualTo(4);
        assertThat(mapping.get().getGridCell().getRowIndex()).isEqualTo(3);
    }

    @Test
    void createGrid_throwsWhenFloorSegmentationNotDone() {
        Floor pendingFloor = Floor.create(floor.getBuilding(), 4); // upload() 호출 안 함 -> PENDING
        floorRepository.save(pendingFloor);

        assertThatThrownBy(() ->
                floorGridService.createOrRegenerateGrid(pendingFloor.getId(),
                        new CreateOrUpdateFloorGridRequest(1.0))
        ).isInstanceOf(ApiException.class);
    }

    @Test
    void createGrid_throwsWhenCellSizeTooSmall() {
        assertThatThrownBy(() ->
                floorGridService.createOrRegenerateGrid(floor.getId(),
                        new CreateOrUpdateFloorGridRequest(0.0001))
        ).isInstanceOf(ApiException.class);
    }

    @Test
    void createGrid_persistsGridConfigToDatabase() {
        floorGridService.createOrRegenerateGrid(floor.getId(), new CreateOrUpdateFloorGridRequest(1.0));

        Floor reloaded = floorRepository.findById(floor.getId()).orElseThrow();
        assertThat(reloaded.getGridCellSizeMeter()).isEqualTo(1.0);
        assertThat(reloaded.getGridRows()).isEqualTo(30);
        assertThat(reloaded.getGridColumns()).isEqualTo(40);
    }

    @Test
    void getGridCells_returnsRequestedPageOnly() {
        floorGridService.createOrRegenerateGrid(
                floor.getId(), new CreateOrUpdateFloorGridRequest(1.0));

        var firstPage = floorGridService.getGridCells(floor.getId(), 0, 100);

        assertThat(firstPage.content()).hasSize(100);
        assertThat(firstPage.totalElements()).isEqualTo(1200);
        assertThat(firstPage.totalPages()).isEqualTo(12);
        assertThat(firstPage.first()).isTrue();
        assertThat(firstPage.last()).isFalse();
        assertThat(firstPage.content().get(0).rowIndex()).isZero();
        assertThat(firstPage.content().get(0).columnIndex()).isZero();
        assertThat(firstPage.content().get(99).rowIndex()).isEqualTo(2);
        assertThat(firstPage.content().get(99).columnIndex()).isEqualTo(19);
    }
}
