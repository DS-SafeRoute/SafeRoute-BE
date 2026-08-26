package com.saferoute.domain.evacuation.grid.service;

import com.saferoute.domain.evacuation.grid.dto.request.UserZoneCreateRequest;
import com.saferoute.domain.evacuation.grid.dto.response.AllUserZoneResponse;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneCellsResponse;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneResponse;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.UserZone;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.UserZoneRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.error.UserZoneErrorCode;
import com.saferoute.global.api.exception.ApiException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserZoneService {

    private final UserZoneRepository userZoneRepository;
    private final FloorRepository floorRepository;
    private final FloorGridCellRepository floorGridCellRepository;

    @Transactional
    public UserZoneResponse create(UUID floorId, UserZoneCreateRequest request){
        if(userZoneRepository.existsByFloor_IdAndName(floorId, request.name())){
            throw new ApiException(UserZoneErrorCode.USER_ZONE_NAME_ALREADY_EXIST);
        }
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        UserZone zone = UserZone.create(floor, request.name());
        userZoneRepository.save(zone);

        List<FloorGridCell> cells = floorGridCellRepository.findAllById(request.cellIds());

        if (cells.size() != request.cellIds().size()) {
            throw new ApiException(UserZoneErrorCode.INVALID_GRID_CELL_REQUEST);
        }
        for (FloorGridCell cell : cells) {
            if (!cell.getFloor().getId().equals(floorId)) {
                throw new ApiException(UserZoneErrorCode.INVALID_GRID_CELL_REQUEST);
            }
        }

        cells.forEach(cell -> cell.assignUserZone(zone));

        return UserZoneResponse.of(zone.getId(), zone.getName(), floor.getFloorNum());
    }

    @Transactional
    public void delete(UUID floorId, UUID userZoneId){
        UserZone userZone = userZoneRepository.findById(userZoneId)
                .orElseThrow(() -> new ApiException(UserZoneErrorCode.USER_ZONE_NOT_FOUND));

        if (!userZone.getFloor().getId().equals(floorId)) {
            throw new ApiException(UserZoneErrorCode.USER_ZONE_NOT_FOUND);
        }

        userZoneRepository.delete(userZone);
    }

    @Transactional(readOnly=true)
    public AllUserZoneResponse findAll(UUID floorId){
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        List<UserZone> userZones = userZoneRepository.findAllByFloor_Id(floorId);

        return AllUserZoneResponse.of(userZones, floor.getFloorNum());
    }

    @Transactional(readOnly = true)
    public UserZoneCellsResponse findUserZone(UUID floorId, UUID userZoneId){
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
        UserZone userZone = userZoneRepository.findById(userZoneId)
                .orElseThrow(() -> new ApiException(UserZoneErrorCode.USER_ZONE_NOT_FOUND));

        List<FloorGridCell> cells = floorGridCellRepository.findAllByUserZone_Id(userZoneId);

        return UserZoneCellsResponse.of(userZone, floor.getFloorNum(), cells);
    }
}
