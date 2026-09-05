package com.saferoute.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.saferoute.domain.analysis.AiAnalysisClient;
import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.domain.analysis.dto.EdgeDto;
import com.saferoute.domain.analysis.dto.NodeDto;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FloorAnalysisServiceTest {

    @InjectMocks
    private FloorAnalysisService floorAnalysisService;

    @Mock
    private FloorRepository floorRepository;
    @Mock
    private MapNodeJpaRepository mapNodeRepository;
    @Mock
    private MapEdgeJpaRepository mapEdgeRepository;
    @Mock
    private AiAnalysisClient aiAnalysisClient;
    @Mock
    private FloorGridService floorGridService;

    @Test
    void persistAnalysisResult_preservesGridConfigAndRemapsGraph() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.create(org.mockito.Mockito.mock(Building.class), 3);
        floor.upload(30.0, 40.0, "floors/third-floor.png");
        floor.applyGridCellConfig(0.5, 60, 80);
        floor.updateSegmentationStatus(SegmentationStatus.PROCESSING);

        AnalyseFloorResponse response = new AnalyseFloorResponse(
                1600,
                900,
                List.of(
                        new NodeDto("node-a", "hallway_1", "HALLWAY", 0.2, 0.5),
                        new NodeDto("node-b", "hallway_2", "HALLWAY", 0.8, 0.5)
                ),
                List.of(new EdgeDto("node-a", "node-b", 24.0, true)),
                Map.of()
        );
        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));

        floorAnalysisService.persistAnalysisResult(floorId, response);

        assertThat(floor.getGridCellSizeMeter()).isEqualTo(0.5);
        assertThat(floor.getGridRows()).isEqualTo(60);
        assertThat(floor.getGridColumns()).isEqualTo(80);
        assertThat(floor.getPlanWidthPx()).isEqualTo(1600);
        assertThat(floor.getPlanHeightPx()).isEqualTo(900);
        assertThat(floor.getSegmentationStatus()).isEqualTo(SegmentationStatus.DONE);

        InOrder inOrder = inOrder(mapEdgeRepository, floorGridService);
        inOrder.verify(mapEdgeRepository).save(org.mockito.ArgumentMatchers.any(MapEdge.class));
        inOrder.verify(floorGridService).remapGraphToExistingGrid(floorId);
    }
}
