package com.saferoute.domain.evacuation.grid.controller;

import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellPageResponse;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridResponse;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/floors/{floorId}/grid")
@RequiredArgsConstructor
@Validated
public class FloorGridController {

    private final FloorGridService floorGridService;

    @GetMapping("/cells")
    public ApiResponse<FloorGridCellPageResponse> getGridCells(
            @PathVariable UUID floorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "500") @Min(1) @Max(2000) int size
    ) {
        return ApiResponse.success(floorGridService.getGridCells(floorId, page, size));
    }

    @PutMapping
    public ApiResponse<FloorGridResponse> createOrRegenerateGrid(
            @PathVariable UUID floorId,
            @Valid @RequestBody CreateOrUpdateFloorGridRequest request
    ) {
        return ApiResponse.success(floorGridService.createOrRegenerateGrid(floorId, request));
    }
}
