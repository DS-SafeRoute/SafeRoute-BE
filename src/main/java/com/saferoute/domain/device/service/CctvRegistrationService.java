package com.saferoute.domain.device.service;

import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CctvRegistrationService {

    private final CctvJpaRepository cctvJpaRepository;
    private final CctvGridCellRepository cctvGridCellRepository;
    private final FloorGridCellRepository floorGridCellRepository;
    private final MapNodeJpaRepository mapNodeJpaRepository;
    private final FloorRepository floorRepository;

    // 코드 유니크 충돌이 발생하면 MapNode/Cctv/매핑 저장을 모두 롤백한 뒤,
    // 호출자인 CctvService가 새 코드로 별도 트랜잭션을 다시 시작한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CctvResponse register(CreateCctvRequest request, String code) {
        Floor floor = floorRepository.findById(request.floorId())
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
        validateGridConfigured(floor);
        List<FloorGridCell> gridCells = findAndValidateGridCells(
                floor.getId(), request.gridCellIds());

        MapNode customNode = mapNodeJpaRepository.save(
                MapNode.createCustom(
                        floor,
                        code,
                        request.name(),
                        request.x(),
                        request.y(),
                        CustomDeviceType.CCTV
                )
        );
        Cctv cctv = cctvJpaRepository.save(Cctv.create(code, request.name(), customNode));
        cctvGridCellRepository.saveAll(
                gridCells.stream()
                        .map(cell -> CctvGridCell.create(cctv, cell))
                        .toList()
        );

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

    private void validateGridConfigured(Floor floor) {
        Double cellSizeMeter = floor.getGridCellSizeMeter();
        if (cellSizeMeter == null || cellSizeMeter <= 0) {
            throw new ApiException(CctvErrorCode.GRID_NOT_CONFIGURED);
        }
    }
}
