package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.device.dto.request.ConfigureCctvGridCellsRequest;
import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CctvServiceTest {

    @Mock CctvJpaRepository cctvJpaRepository;
    @Mock CctvGridCellRepository cctvGridCellRepository;
    @Mock FloorGridCellRepository floorGridCellRepository;
    @Mock CctvRegistrationService cctvRegistrationService;

    private CctvService cctvService;

    @BeforeEach
    void setUp() {
        cctvService = new CctvService(
                cctvJpaRepository,
                cctvGridCellRepository,
                floorGridCellRepository,
                cctvRegistrationService
        );
    }

    @Test
    @DisplayName("CCTV 등록 시 생성한 코드를 별도 트랜잭션 등록 서비스에 전달한다")
    void createCctv_success() {
        CreateCctvRequest request = request();
        CctvResponse expected = org.mockito.Mockito.mock(CctvResponse.class);
        given(cctvRegistrationService.register(any(), any())).willReturn(expected);

        CctvResponse response = cctvService.createCctv(request);

        assertThat(response).isSameAs(expected);
        verify(cctvRegistrationService).register(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.matches("CCTV_[0-9A-F]{8}")
        );
    }

    @Test
    @DisplayName("CCTV 코드 유니크 충돌 시 새 코드로 최대 세 번 재시도한다")
    void createCctv_retriesUniqueConflict() {
        CreateCctvRequest request = request();
        CctvResponse expected = org.mockito.Mockito.mock(CctvResponse.class);
        given(cctvRegistrationService.register(any(), any()))
                .willThrow(new DataIntegrityViolationException("duplicate code"))
                .willThrow(new DataIntegrityViolationException("duplicate code"))
                .willReturn(expected);

        assertThat(cctvService.createCctv(request)).isSameAs(expected);
        verify(cctvRegistrationService, times(3)).register(any(), any());
    }

    @Test
    @DisplayName("CCTV 코드 충돌이 세 번 계속되면 명시적인 실패 응답을 반환한다")
    void createCctv_failsAfterRetryLimit() {
        given(cctvRegistrationService.register(any(), any()))
                .willThrow(new DataIntegrityViolationException("duplicate code"));

        assertThatThrownBy(() -> cctvService.createCctv(request()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(CctvErrorCode.CCTV_CODE_GENERATION_FAILED);
        verify(cctvRegistrationService, times(3)).register(any(), any());
    }

    @Test
    @DisplayName("CCTV 목록의 감시 셀 매핑을 CCTV ID 목록으로 한 번에 조회한다")
    void getCctvs_loadsMappingsInBatch() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Cctv firstCctv = cctv(firstId);
        Cctv secondCctv = cctv(secondId);
        CctvGridCell firstMapping = mapping(firstCctv, cell(0, 0));
        CctvGridCell secondMapping = mapping(secondCctv, cell(0, 1));
        given(cctvJpaRepository.findAllWithLocation()).willReturn(List.of(firstCctv, secondCctv));
        given(cctvGridCellRepository.findAllByCctvIdsWithGridCell(anyList()))
                .willReturn(List.of(firstMapping, secondMapping));

        List<CctvResponse> responses = cctvService.getCctvs(null);

        assertThat(responses).hasSize(2);
        verify(cctvGridCellRepository).findAllByCctvIdsWithGridCell(List.of(firstId, secondId));
        verify(cctvGridCellRepository, never())
                .findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(any());
    }

    @Test
    @DisplayName("감시 영역 수정 시 검증을 마친 뒤 기존 매핑을 교체한다")
    void configureGridCells_success() {
        UUID cctvId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        Floor floor = org.mockito.Mockito.mock(Floor.class);
        MapNode node = org.mockito.Mockito.mock(MapNode.class);
        Cctv cctv = cctv(cctvId);
        FloorGridCell cell = cell(1, 2);
        given(floor.getId()).willReturn(floorId);
        given(floor.getGridCellSizeMeter()).willReturn(0.5);
        given(node.getFloor()).willReturn(floor);
        given(cctv.getCustomNode()).willReturn(node);
        given(cell.getId()).willReturn(cellId);
        given(cell.getFloor()).willReturn(floor);
        given(cell.isWalkable()).willReturn(true);
        given(cctvJpaRepository.findByIdWithLocation(cctvId)).willReturn(Optional.of(cctv));
        given(floorGridCellRepository.findAllById(List.of(cellId))).willReturn(List.of(cell));

        cctvService.configureGridCells(
                cctvId,
                new ConfigureCctvGridCellsRequest(List.of(cellId))
        );

        verify(cctvGridCellRepository).deleteAllByCctvId(cctvId);
        verify(cctvGridCellRepository).saveAll(any());
    }

    private CreateCctvRequest request() {
        return new CreateCctvRequest(
                "동쪽 복도 CCTV",
                UUID.randomUUID(),
                0.4,
                0.3,
                List.of(UUID.randomUUID())
        );
    }

    private Cctv cctv(UUID id) {
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        MapNode node = org.mockito.Mockito.mock(MapNode.class);
        Floor floor = org.mockito.Mockito.mock(Floor.class);
        org.mockito.Mockito.lenient().when(cctv.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(cctv.getCustomNode()).thenReturn(node);
        org.mockito.Mockito.lenient().when(node.getFloor()).thenReturn(floor);
        org.mockito.Mockito.lenient().when(floor.getGridCellSizeMeter()).thenReturn(0.5);
        return cctv;
    }

    private FloorGridCell cell(int row, int column) {
        FloorGridCell cell = org.mockito.Mockito.mock(FloorGridCell.class);
        org.mockito.Mockito.lenient().when(cell.getRowIndex()).thenReturn(row);
        org.mockito.Mockito.lenient().when(cell.getColumnIndex()).thenReturn(column);
        return cell;
    }

    private CctvGridCell mapping(Cctv cctv, FloorGridCell cell) {
        CctvGridCell mapping = org.mockito.Mockito.mock(CctvGridCell.class);
        given(mapping.getCctv()).willReturn(cctv);
        given(mapping.getGridCell()).willReturn(cell);
        return mapping;
    }
}
