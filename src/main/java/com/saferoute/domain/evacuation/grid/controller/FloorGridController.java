package com.saferoute.domain.evacuation.grid.controller;

import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridResponse;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellResponse;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/floors/{floorId}/grid")
@RequiredArgsConstructor
public class FloorGridController {

    private final FloorGridService floorGridService;

    @GetMapping("/cells")
    public ApiResponse<List<FloorGridCellResponse>> getGridCells(@PathVariable UUID floorId) {
        return ApiResponse.success(floorGridService.getGridCells(floorId));
    }

    @PutMapping
    public ApiResponse<FloorGridResponse> createOrRegenerateGrid(
            @PathVariable UUID floorId,
            @Valid @RequestBody CreateOrUpdateFloorGridRequest request
    ) {
        return ApiResponse.success(floorGridService.createOrRegenerateGrid(floorId, request));
    }
}
