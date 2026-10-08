package com.saferoute.domain.evacuation.recalculation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.telemetry.dynamo.entity.CurrentCctvStateItem;
import com.saferoute.domain.telemetry.dynamo.repository.CurrentCctvStateRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentCongestionWeightProviderTest {

    private final UUID sessionId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    @InjectMocks
    private CurrentCongestionWeightProvider provider;

    @Mock
    private CurrentCctvStateRepository currentCctvStateRepository;

    @Mock
    private CctvJpaRepository cctvJpaRepository;

    @Mock
    private CctvGridCellRepository cctvGridCellRepository;

    @Mock
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;

    private CurrentCctvStateItem state(String cctvCode, CongestionLevel level) {
        return CurrentCctvStateItem.create(sessionId, cctvCode, 1.0, 1, 1.0, level, 1L, 1L);
    }

    private Cctv cctv(UUID id, String code) {
        Cctv cctv = mock(Cctv.class);
        given(cctv.getId()).willReturn(id);
        given(cctv.getCode()).willReturn(code);
        return cctv;
    }

    private FloorGridCell cell(UUID id) {
        FloorGridCell cell = mock(FloorGridCell.class);
        given(cell.getId()).willReturn(id);
        return cell;
    }

    private CctvGridCell watching(Cctv cctv, FloorGridCell cell) {
        CctvGridCell mapping = mock(CctvGridCell.class);
        given(mapping.getCctv()).willReturn(cctv);
        given(mapping.getGridCell()).willReturn(cell);
        return mapping;
    }

    private MapEdgeGridCell crossing(UUID edgeId, FloorGridCell cell) {
        MapEdge edge = mock(MapEdge.class);
        given(edge.getId()).willReturn(edgeId);
        MapEdgeGridCell mapping = mock(MapEdgeGridCell.class);
        given(mapping.getMapEdge()).willReturn(edge);
        given(mapping.getGridCell()).willReturn(cell);
        return mapping;
    }

    @Test
    @DisplayName("혼잡한 CCTV가 감시하는 엣지에만 레벨별 배율을 부여한다")
    void currentMultipliers_mapsCongestedCctvToItsEdges() {
        UUID crowdedCctvId = UUID.randomUUID();
        UUID cautionCctvId = UUID.randomUUID();
        UUID crowdedEdgeId = UUID.randomUUID();
        UUID cautionEdgeId = UUID.randomUUID();
        FloorGridCell crowdedCell = cell(UUID.randomUUID());
        FloorGridCell cautionCell = cell(UUID.randomUUID());
        Cctv crowdedCctv = cctv(crowdedCctvId, "CCTV-1");
        Cctv cautionCctv = cctv(cautionCctvId, "CCTV-2");
        Cctv normalCctv = mock(Cctv.class);
        given(normalCctv.getCode()).willReturn("CCTV-3");

        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString())).willReturn(List.of(
                state("CCTV-1", CongestionLevel.CROWDED),
                state("CCTV-2", CongestionLevel.CAUTION),
                state("CCTV-3", CongestionLevel.NORMAL)));
        given(cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId))
                .willReturn(List.of(crowdedCctv, cautionCctv, normalCctv));
        List<CctvGridCell> cctvCells = List.of(watching(crowdedCctv, crowdedCell), watching(cautionCctv, cautionCell));
        List<MapEdgeGridCell> edgeCells =
                List.of(crossing(crowdedEdgeId, crowdedCell), crossing(cautionEdgeId, cautionCell));
        given(cctvGridCellRepository.findAllByCctvIdsWithGridCell(any())).willReturn(cctvCells);
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(any())).willReturn(edgeCells);

        Map<UUID, Double> multipliers = provider.currentMultipliers(sessionId, floorId);

        assertThat(multipliers).containsOnly(
                Map.entry(crowdedEdgeId, RouteRecalculationService.CROWDED_WEIGHT_MULTIPLIER),
                Map.entry(cautionEdgeId, RouteRecalculationService.CAUTION_WEIGHT_MULTIPLIER));
    }

    @Test
    @DisplayName("VERY_CROWDED도 제외가 아니라 큰 배율로만 반영한다")
    void currentMultipliers_veryCrowdedIsSoftPenalty() {
        UUID cctvId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();
        FloorGridCell cell = cell(UUID.randomUUID());
        Cctv cctv = cctv(cctvId, "CCTV-1");

        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString()))
                .willReturn(List.of(state("CCTV-1", CongestionLevel.VERY_CROWDED)));
        given(cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId)).willReturn(List.of(cctv));
        List<CctvGridCell> cctvCells = List.of(watching(cctv, cell));
        List<MapEdgeGridCell> edgeCells = List.of(crossing(edgeId, cell));
        given(cctvGridCellRepository.findAllByCctvIdsWithGridCell(any())).willReturn(cctvCells);
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(any())).willReturn(edgeCells);

        assertThat(provider.currentMultipliers(sessionId, floorId))
                .containsOnly(Map.entry(edgeId, CurrentCongestionWeightProvider.VERY_CROWDED_WEIGHT_MULTIPLIER));
    }

    @Test
    @DisplayName("여러 CCTV가 같은 엣지를 감시하면 가장 큰 배율을 쓴다")
    void currentMultipliers_sharedEdgeTakesMaxMultiplier() {
        UUID edgeId = UUID.randomUUID();
        FloorGridCell cautionCell = cell(UUID.randomUUID());
        FloorGridCell crowdedCell = cell(UUID.randomUUID());
        Cctv cautionCctv = cctv(UUID.randomUUID(), "CCTV-1");
        Cctv crowdedCctv = cctv(UUID.randomUUID(), "CCTV-2");

        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString())).willReturn(List.of(
                state("CCTV-1", CongestionLevel.CAUTION), state("CCTV-2", CongestionLevel.CROWDED)));
        given(cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId))
                .willReturn(List.of(cautionCctv, crowdedCctv));
        List<CctvGridCell> cctvCells = List.of(watching(cautionCctv, cautionCell), watching(crowdedCctv, crowdedCell));
        List<MapEdgeGridCell> edgeCells = List.of(crossing(edgeId, cautionCell), crossing(edgeId, crowdedCell));
        given(cctvGridCellRepository.findAllByCctvIdsWithGridCell(any())).willReturn(cctvCells);
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(any())).willReturn(edgeCells);

        assertThat(provider.currentMultipliers(sessionId, floorId))
                .containsOnly(Map.entry(edgeId, RouteRecalculationService.CROWDED_WEIGHT_MULTIPLIER));
    }

    @Test
    @DisplayName("제외 지정한 CCTV의 혼잡은 집계에서 빼고 나머지 CCTV의 혼잡은 그대로 반영한다")
    void currentMultipliers_excludedCctvIsIgnoredOthersKept() {
        UUID keptEdgeId = UUID.randomUUID();
        FloorGridCell keptCell = cell(UUID.randomUUID());
        Cctv keptCctv = cctv(UUID.randomUUID(), "CCTV-KEPT");
        Cctv endedCctv = mock(Cctv.class);
        given(endedCctv.getCode()).willReturn("CCTV-ENDED");

        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString())).willReturn(List.of(
                state("CCTV-KEPT", CongestionLevel.CROWDED), state("CCTV-ENDED", CongestionLevel.VERY_CROWDED)));
        given(cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId)).willReturn(List.of(keptCctv, endedCctv));
        List<CctvGridCell> cctvCells = List.of(watching(keptCctv, keptCell));
        List<MapEdgeGridCell> edgeCells = List.of(crossing(keptEdgeId, keptCell));
        given(cctvGridCellRepository.findAllByCctvIdsWithGridCell(any())).willReturn(cctvCells);
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(any())).willReturn(edgeCells);

        assertThat(provider.currentMultipliers(sessionId, floorId, java.util.Set.of("CCTV-ENDED")))
                .containsOnly(Map.entry(keptEdgeId, RouteRecalculationService.CROWDED_WEIGHT_MULTIPLIER));
    }

    @Test
    @DisplayName("제외한 CCTV 말고는 혼잡한 곳이 없으면 이후 조회 없이 빈 맵을 반환한다")
    void currentMultipliers_onlyExcludedCctvCongested_returnsEmpty() {
        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString()))
                .willReturn(List.of(state("CCTV-ENDED", CongestionLevel.CROWDED)));

        assertThat(provider.currentMultipliers(sessionId, floorId, java.util.Set.of("CCTV-ENDED"))).isEmpty();
        verifyNoInteractions(cctvJpaRepository, cctvGridCellRepository, mapEdgeGridCellRepository);
    }

    @Test
    @DisplayName("혼잡한 CCTV가 없으면 이후 조회 없이 빈 맵을 반환한다")
    void currentMultipliers_noCongestion_returnsEmptyWithoutFurtherQueries() {
        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString()))
                .willReturn(List.of(state("CCTV-1", CongestionLevel.NORMAL)));

        assertThat(provider.currentMultipliers(sessionId, floorId)).isEmpty();
        verifyNoInteractions(cctvJpaRepository, cctvGridCellRepository, mapEdgeGridCellRepository);
    }

    @Test
    @DisplayName("혼잡한 CCTV가 다른 층에 있으면 이 층에는 영향이 없다")
    void currentMultipliers_congestedCctvOnOtherFloor_returnsEmpty() {
        given(currentCctvStateRepository.findAllBySessionId(sessionId.toString()))
                .willReturn(List.of(state("CCTV-OTHER", CongestionLevel.CROWDED)));
        given(cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId)).willReturn(List.of());

        assertThat(provider.currentMultipliers(sessionId, floorId)).isEmpty();
        verifyNoInteractions(cctvGridCellRepository, mapEdgeGridCellRepository);
    }
}
