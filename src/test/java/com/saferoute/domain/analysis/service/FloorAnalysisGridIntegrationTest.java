package com.saferoute.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.domain.analysis.dto.EdgeDto;
import com.saferoute.domain.analysis.dto.NodeDto;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.NodeGridCellRepository;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FloorAnalysisGridIntegrationTest {

    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private FloorRepository floorRepository;
    @Autowired
    private FloorGridService floorGridService;
    @Autowired
    private FloorAnalysisService floorAnalysisService;
    @Autowired
    private FloorGridCellRepository floorGridCellRepository;
    @Autowired
    private MapNodeJpaRepository mapNodeRepository;
    @Autowired
    private MapEdgeJpaRepository mapEdgeRepository;
    @Autowired
    private NodeGridCellRepository nodeGridCellRepository;
    @Autowired
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;
    @Autowired
    private CctvJpaRepository cctvRepository;
    @Autowired
    private CctvGridCellRepository cctvGridCellRepository;

    @Test
    void gridCreatedBeforeAnalysis_isPreservedAndConnectsCctvCellToAnalyzedEdge() {
        Building building = buildingRepository.save(
                Building.create("분석관", "서울특별시 안전구", BuildingType.CLASSROOM, "SafeRoute School"));
        Floor floor = Floor.create(building, 3);
        floor.upload(3.0, 4.0, "floors/third-floor.png");
        floorRepository.save(floor);

        floorGridService.createOrRegenerateGrid(
                floor.getId(), new CreateOrUpdateFloorGridRequest(0.5));
        List<java.util.UUID> gridCellIdsBeforeAnalysis = floorGridCellRepository
                .findAllByFloor_Id(floor.getId()).stream()
                .map(cell -> cell.getId())
                .toList();

        AnalyseFloorResponse response = new AnalyseFloorResponse(
                1600,
                900,
                List.of(
                        new NodeDto("node-a", "hallway_1", "HALLWAY", 0.1, 0.5),
                        new NodeDto("node-b", "hallway_2", "HALLWAY", 0.9, 0.5)
                ),
                List.of(new EdgeDto("node-a", "node-b", 3.2, true)),
                Map.of()
        );

        floorAnalysisService.persistAnalysisResult(floor.getId(), response);

        Floor analyzedFloor = floorRepository.findById(floor.getId()).orElseThrow();
        assertThat(analyzedFloor.getGridCellSizeMeter()).isEqualTo(0.5);
        assertThat(analyzedFloor.getGridRows()).isEqualTo(6);
        assertThat(analyzedFloor.getGridColumns()).isEqualTo(8);
        assertThat(analyzedFloor.getSegmentationStatus()).isEqualTo(SegmentationStatus.DONE);
        assertThat(floorGridCellRepository.findAllByFloor_Id(floor.getId()))
                .extracting(cell -> cell.getId())
                .containsExactlyInAnyOrderElementsOf(gridCellIdsBeforeAnalysis);

        List<MapNode> analyzedNodes = mapNodeRepository.findAllByFloor_Id(floor.getId());
        assertThat(analyzedNodes).hasSize(2);
        assertThat(nodeGridCellRepository.findAllByNode_IdIn(
                analyzedNodes.stream().map(MapNode::getId).toList())).hasSize(2);

        MapEdge analyzedEdge = mapEdgeRepository.findAllByFloor_Id(floor.getId()).get(0);
        List<MapEdgeGridCell> edgeMappings =
                mapEdgeGridCellRepository.findAllByMapEdge_Id(analyzedEdge.getId());
        assertThat(edgeMappings).isNotEmpty();

        MapNode cctvNode = mapNodeRepository.save(
                MapNode.createCustom(floor, "CCTV_TEST", "복도 CCTV", 0.5, 0.5,
                        com.saferoute.domain.evacuation.graph.entity.CustomDeviceType.CCTV));
        Cctv cctv = cctvRepository.save(Cctv.create("CCTV_TEST", "복도 CCTV", cctvNode));
        cctvGridCellRepository.save(CctvGridCell.create(cctv, edgeMappings.get(0).getGridCell()));

        List<java.util.UUID> monitoredCellIds = cctvGridCellRepository
                .findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctv.getId())
                .stream()
                .map(mapping -> mapping.getGridCell().getId())
                .toList();
        assertThat(mapEdgeGridCellRepository.findAllByGridCell_IdIn(monitoredCellIds))
                .extracting(mapping -> mapping.getMapEdge().getId())
                .contains(analyzedEdge.getId());
    }
}
