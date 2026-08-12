package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.device.dto.request.ConfigureCctvGridCellsRequest;
import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CctvServiceTest {

    @Mock CctvJpaRepository cctvJpaRepository;
    @Mock CctvGridCellRepository cctvGridCellRepository;
    @Mock FloorGridCellRepository floorGridCellRepository;
    @Mock MapNodeJpaRepository mapNodeJpaRepository;
    @Mock FloorRepository floorRepository;

    private CctvService cctvService;
    private Floor floor;
    private UUID floorId;

    @BeforeEach
    void setUp() {
        cctvService = new CctvService(
                cctvJpaRepository,
                cctvGridCellRepository,
                floorGridCellRepository,
                mapNodeJpaRepository,
                floorRepository
        );
        floor = org.mockito.Mockito.mock(Floor.class);
        floorId = UUID.randomUUID();
        given(floor.getId()).willReturn(floorId);
        given(floor.getGridCellSizeMeter()).willReturn(0.5);
    }

    @Test
    @DisplayName("CCTV 등록 시 위치 노드와 감시 셀을 한 번에 저장하고 면적을 계산한다")
    void createCctv_success() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        FloorGridCell first = cell(firstId, floor, 0, 0, true);
        FloorGridCell second = cell(secondId, floor, 0, 1, true);
        CreateCctvRequest request = new CreateCctvRequest(
                "동쪽 복도 CCTV", floorId, 0.4, 0.3, List.of(firstId, secondId));

        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));
        given(floorGridCellRepository.findAllById(request.gridCellIds()))
                .willReturn(List.of(second, first));
        given(cctvJpaRepository.existsByCode(any())).willReturn(false);
        given(mapNodeJpaRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(cctvJpaRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CctvResponse response = cctvService.createCctv(request);

        assertThat(response.code()).startsWith("CCTV_");
        assertThat(response.x()).isEqualTo(0.4);
        assertThat(response.y()).isEqualTo(0.3);
        assertThat(response.monitoredGridCellCount()).isEqualTo(2);
        assertThat(response.monitoredAreaM2()).isEqualTo(0.5);
        assertThat(response.gridCells()).extracting("id").containsExactly(firstId, secondId);

        ArgumentCaptor<List<CctvGridCell>> mappings = ArgumentCaptor.forClass(List.class);
        verify(cctvGridCellRepository).saveAll(mappings.capture());
        assertThat(mappings.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("중복 GridCell 요청은 저장 전에 거부한다")
    void createCctv_duplicateGridCells() {
        UUID cellId = UUID.randomUUID();
        CreateCctvRequest request = new CreateCctvRequest(
                "동쪽 복도 CCTV", floorId, 0.4, 0.3, List.of(cellId, cellId));
        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));

        assertThatThrownBy(() -> cctvService.createCctv(request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(CctvErrorCode.DUPLICATE_GRID_CELL);

        verify(mapNodeJpaRepository, never()).save(any());
        verify(cctvJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 층의 GridCell은 CCTV 감시 영역으로 등록할 수 없다")
    void createCctv_gridCellFloorMismatch() {
        UUID cellId = UUID.randomUUID();
        Floor otherFloor = org.mockito.Mockito.mock(Floor.class);
        given(otherFloor.getId()).willReturn(UUID.randomUUID());
        FloorGridCell cell = cell(cellId, otherFloor, 0, 0, true);
        CreateCctvRequest request = new CreateCctvRequest(
                "동쪽 복도 CCTV", floorId, 0.4, 0.3, List.of(cellId));
        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));
        given(floorGridCellRepository.findAllById(request.gridCellIds())).willReturn(List.of(cell));

        assertThatThrownBy(() -> cctvService.createCctv(request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(CctvErrorCode.GRID_CELL_FLOOR_MISMATCH);
    }

    @Test
    @DisplayName("보행 불가능한 GridCell은 CCTV 감시 영역으로 등록할 수 없다")
    void createCctv_nonWalkableGridCell() {
        UUID cellId = UUID.randomUUID();
        FloorGridCell cell = cell(cellId, floor, 0, 0, false);
        CreateCctvRequest request = new CreateCctvRequest(
                "동쪽 복도 CCTV", floorId, 0.4, 0.3, List.of(cellId));
        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));
        given(floorGridCellRepository.findAllById(request.gridCellIds())).willReturn(List.of(cell));

        assertThatThrownBy(() -> cctvService.createCctv(request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(CctvErrorCode.NON_WALKABLE_GRID_CELL);
    }

    @Test
    @DisplayName("감시 영역 수정 시 검증을 마친 뒤 기존 매핑을 교체한다")
    void configureGridCells_success() {
        UUID cctvId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        MapNode node = org.mockito.Mockito.mock(MapNode.class);
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        FloorGridCell cell = cell(cellId, floor, 1, 2, true);
        given(cctv.getId()).willReturn(cctvId);
        given(cctv.getCustomNode()).willReturn(node);
        given(node.getFloor()).willReturn(floor);
        given(cctvJpaRepository.findById(cctvId)).willReturn(Optional.of(cctv));
        given(floorGridCellRepository.findAllById(List.of(cellId))).willReturn(List.of(cell));

        cctvService.configureGridCells(
                cctvId,
                new ConfigureCctvGridCellsRequest(List.of(cellId))
        );

        verify(cctvGridCellRepository).deleteAllByCctvId(cctvId);
        verify(cctvGridCellRepository).saveAll(any());
    }

    private FloorGridCell cell(
            UUID id,
            Floor owningFloor,
            int row,
            int column,
            boolean walkable
    ) {
        FloorGridCell cell = org.mockito.Mockito.mock(FloorGridCell.class);
        org.mockito.Mockito.lenient().when(cell.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(cell.getFloor()).thenReturn(owningFloor);
        org.mockito.Mockito.lenient().when(cell.getRowIndex()).thenReturn(row);
        org.mockito.Mockito.lenient().when(cell.getColumnIndex()).thenReturn(column);
        org.mockito.Mockito.lenient().when(cell.isWalkable()).thenReturn(walkable);
        return cell;
    }
}
