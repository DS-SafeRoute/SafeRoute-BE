package com.saferoute.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.saferoute.domain.analysis.AiAnalysisClient;
import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.domain.analysis.dto.EdgeDto;
import com.saferoute.domain.analysis.dto.NodeDto;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.GridErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class FloorAnalysisRollbackIntegrationTest {

    private static final String SCHOOL_NAME = "Rollback Test School";

    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private FloorRepository floorRepository;
    @Autowired
    private FloorGridService floorGridService;
    @Autowired
    private FloorAnalysisService floorAnalysisService;
    @Autowired
    private FloorAnalysisStatusService statusService;
    @Autowired
    private FloorGridCellRepository floorGridCellRepository;
    @Autowired
    private MapNodeJpaRepository mapNodeRepository;
    @Autowired
    private MapEdgeJpaRepository mapEdgeRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    @Test
    void gridCellMismatch_rollsBackGraphReplacementAndKeepsFailedStatus() {
        UUID floorId = transactionTemplate.execute(status -> createFloorWithExistingGraph());
        floorGridService.createOrRegenerateGrid(
                floorId, new CreateOrUpdateFloorGridRequest(0.5));

        transactionTemplate.executeWithoutResult(status -> {
            FloorGridCell oneCell = floorGridCellRepository.findAllByFloor_Id(floorId).get(0);
            floorGridCellRepository.delete(oneCell);
        });
        statusService.markAsProcessing(floorId, SCHOOL_NAME);

        AnalyseFloorResponse response = new AnalyseFloorResponse(
                1600,
                900,
                List.of(
                        new NodeDto("new-a", "NEW_A", "HALLWAY", 0.2, 0.5),
                        new NodeDto("new-b", "NEW_B", "HALLWAY", 0.8, 0.5)),
                List.of(new EdgeDto("new-a", "new-b", 2.4, true)),
                Map.of());
        given(aiAnalysisClient.analyze("floors/rollback.png", 4.0, 3.0))
                .willReturn(response);

        assertThatThrownBy(() -> floorAnalysisService.analyzeFloor(floorId))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(GridErrorCode.GRID_CELL_NOT_FOUND);

        Floor failedFloor = floorRepository.findById(floorId).orElseThrow();
        assertThat(failedFloor.getSegmentationStatus()).isEqualTo(SegmentationStatus.FAILED);
        assertThat(mapNodeRepository.findAllByFloor_Id(floorId))
                .extracting(MapNode::getCode)
                .containsExactlyInAnyOrder("OLD_A", "OLD_B");
        assertThat(mapEdgeRepository.findAllByFloor_Id(floorId))
                .singleElement()
                .extracting(MapEdge::getDistance)
                .isEqualTo(2.0);
    }

    private UUID createFloorWithExistingGraph() {
        Building building = buildingRepository.save(Building.create(
                "롤백 분석관", "부산광역시 안전구", BuildingType.CLASSROOM, SCHOOL_NAME));
        Floor floor = Floor.create(building, 1);
        floor.upload(3.0, 4.0, "floors/rollback.png");
        floor.updateSegmentationStatus(SegmentationStatus.DONE);
        floorRepository.save(floor);

        MapNode from = mapNodeRepository.save(MapNode.create(
                floor, "OLD_A", NodeType.HALLWAY, "기존 A", 0.1, 0.5, false));
        MapNode to = mapNodeRepository.save(MapNode.create(
                floor, "OLD_B", NodeType.HALLWAY, "기존 B", 0.9, 0.5, false));
        mapEdgeRepository.save(MapEdge.create(floor, from, to, 2.0, true));
        return floor.getId();
    }
}
