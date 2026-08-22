package com.saferoute.domain.device.service;

import com.saferoute.domain.device.dto.request.ConfigureCctvGridCellsRequest;
import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.dto.response.CctvRegistrationResponse;
import com.saferoute.domain.device.dto.response.DeviceTokenIssueResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.congestion.service.CongestionConfigService;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.DeviceErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DeviceTokenService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CctvService {

    private final CctvJpaRepository cctvJpaRepository;
    private final CctvGridCellRepository cctvGridCellRepository;
    private final FloorGridCellRepository floorGridCellRepository;
    private final CctvCodeAllocator cctvCodeAllocator;
    private final CctvRegistrationService cctvRegistrationService;
    private final DeviceTokenService deviceTokenService;
    private final CongestionConfigService congestionConfigService;

    public CctvRegistrationResponse createCctv(CreateCctvRequest request) {
        return cctvRegistrationService.register(request, cctvCodeAllocator.allocate());
    }

    @Transactional
    public DeviceTokenIssueResponse issueDeviceToken(UUID cctvId) {
        Cctv cctv = cctvJpaRepository.findByIdForDeviceTokenIssue(cctvId)
                .orElseThrow(() -> new ApiException(CctvErrorCode.CCTV_NOT_FOUND));
        if (cctv.getDeviceTokenHash() != null) {
            throw new ApiException(DeviceErrorCode.DEVICE_TOKEN_ALREADY_ISSUED);
        }

        DeviceTokenService.IssuedDeviceToken issuedToken = deviceTokenService.issue();
        cctv.issueDeviceToken(issuedToken.hash());
        return new DeviceTokenIssueResponse(issuedToken.rawToken());
    }

    public List<CctvResponse> getCctvs(UUID floorId) {
        List<Cctv> cctvs = floorId == null
                ? cctvJpaRepository.findAllWithLocation()
                : cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId);
        if (cctvs.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<FloorGridCell>> gridCellsByCctvId = cctvGridCellRepository
                .findAllByCctvIdsWithGridCell(cctvs.stream().map(Cctv::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        mapping -> mapping.getCctv().getId(),
                        Collectors.mapping(CctvGridCell::getGridCell, Collectors.toList())
                ));

        return cctvs.stream()
                .map(cctv -> CctvResponse.of(
                        cctv,
                        gridCellsByCctvId.getOrDefault(cctv.getId(), List.of())
                ))
                .toList();
    }

    public CctvResponse getCctv(UUID cctvId) {
        return toResponse(findCctvOrThrow(cctvId));
    }

    public CctvResponse getGridCells(UUID cctvId) {
        return getCctv(cctvId);
    }

    @Transactional
    public CctvResponse configureGridCells(
            UUID cctvId,
            ConfigureCctvGridCellsRequest request
    ) {
        Cctv cctv = findCctvOrThrow(cctvId);
        Floor floor = cctv.getCustomNode().getFloor();
        validateGridConfigured(floor);
        List<FloorGridCell> gridCells = findAndValidateGridCells(
                floor.getId(), request.gridCellIds());

        cctvGridCellRepository.deleteAllByCctvId(cctvId);
        saveMappings(cctv, gridCells);
        congestionConfigService.incrementVersionForGridChange();
        return CctvResponse.of(cctv, gridCells);
    }

    @Transactional
    public CctvResponse enableCctv(UUID cctvId) {
        Cctv cctv = findCctvOrThrow(cctvId);
        cctv.enable();
        return toResponse(cctv);
    }

    @Transactional
    public CctvResponse disableCctv(UUID cctvId) {
        Cctv cctv = findCctvOrThrow(cctvId);
        cctv.disable();
        return toResponse(cctv);
    }

    private CctvResponse toResponse(Cctv cctv) {
        List<FloorGridCell> gridCells = cctvGridCellRepository
                .findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctv.getId())
                .stream()
                .map(CctvGridCell::getGridCell)
                .toList();
        return CctvResponse.of(cctv, gridCells);
    }

    private List<FloorGridCell> findAndValidateGridCells(
            UUID floorId,
            List<UUID> gridCellIds
    ) {
        if (new HashSet<>(gridCellIds).size() != gridCellIds.size()) {
            throw new ApiException(CctvErrorCode.DUPLICATE_GRID_CELL);
        }

        List<FloorGridCell> gridCells = floorGridCellRepository.findAllById(gridCellIds);
        if (gridCells.size() != gridCellIds.size()) {
            throw new ApiException(CctvErrorCode.GRID_CELL_NOT_FOUND);
        }
        if (gridCells.stream().anyMatch(cell -> !cell.getFloor().getId().equals(floorId))) {
            throw new ApiException(CctvErrorCode.GRID_CELL_FLOOR_MISMATCH);
        }
        if (gridCells.stream().anyMatch(cell -> !cell.isWalkable())) {
            throw new ApiException(CctvErrorCode.NON_WALKABLE_GRID_CELL);
        }

        return gridCells.stream()
                .sorted(Comparator.comparingInt(FloorGridCell::getRowIndex)
                        .thenComparingInt(FloorGridCell::getColumnIndex))
                .toList();
    }

    private void saveMappings(Cctv cctv, List<FloorGridCell> gridCells) {
        cctvGridCellRepository.saveAll(
                gridCells.stream()
                        .map(cell -> CctvGridCell.create(cctv, cell))
                        .toList()
        );
    }

    private void validateGridConfigured(Floor floor) {
        Double cellSizeMeter = floor.getGridCellSizeMeter();
        if (cellSizeMeter == null || cellSizeMeter <= 0) {
            throw new ApiException(CctvErrorCode.GRID_NOT_CONFIGURED);
        }
    }

    private Cctv findCctvOrThrow(UUID cctvId) {
        return cctvJpaRepository.findByIdWithLocation(cctvId)
                .orElseThrow(() -> new ApiException(CctvErrorCode.CCTV_NOT_FOUND));
    }

}
