package com.saferoute.domain.evacuation.grid.controller;

import com.saferoute.domain.evacuation.grid.dto.request.UserZoneCreateRequest;
import com.saferoute.domain.evacuation.grid.dto.response.AllUserZoneResponse;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneCellsResponse;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneResponse;
import com.saferoute.domain.evacuation.grid.service.UserZoneService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "유저 구역", description = "유저구역 등록/조회/수정/삭제 API")
@RestController
@RequestMapping("/api/v1/floors/{floorId}/user-zones")
@RequiredArgsConstructor
@Validated
public class UserZoneController {

    private final UserZoneService userZoneService;

    @PostMapping
    public ApiResponse<UserZoneResponse> createUserZone(
            @PathVariable UUID floorId,
            @RequestBody UserZoneCreateRequest request
    ) {
        return ApiResponse.success(userZoneService.create(floorId, request));
    }
    
    @DeleteMapping("/{userZoneId}")
    public ResponseEntity<Void> deleteUserZone(
            @PathVariable UUID floorId,
            @PathVariable UUID userZoneId
    ){
        userZoneService.delete(floorId, userZoneId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ApiResponse<AllUserZoneResponse> findAllUserZone(
            @PathVariable UUID floorId
    ){
        return ApiResponse.success(userZoneService.findAll(floorId));
    }

    @GetMapping("/{userZoneId}")
    public ApiResponse<UserZoneCellsResponse> findUserZone(
            @PathVariable UUID floorId,
            @PathVariable UUID userZoneId
    ){
        return ApiResponse.success(userZoneService.findUserZone(floorId, userZoneId));
    }
}
