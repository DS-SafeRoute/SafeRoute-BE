package com.saferoute.domain.evacuation.grid.controller;

import com.saferoute.domain.evacuation.grid.dto.request.UserZoneCreateRequest;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneResponse;
import com.saferoute.domain.evacuation.grid.service.UserZoneService;
import com.saferoute.global.api.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/floors/userZone")
@RequiredArgsConstructor
@Validated
public class UserZoneController {

    private final UserZoneService userZoneService;

    @PostMapping("/{floorId}")
    public ApiResponse<UserZoneResponse> createUserZone(
            @PathVariable UUID floorId,
            UserZoneCreateRequest request
    ) {
        return ApiResponse.success(userZoneService.create(floorId, request));
    }
    
    @DeleteMapping("/{floorId}/{userZoneId}")
    public ResponseEntity<Void> deleteUserZone(
            @PathVariable UUID floorId,
            @PathVariable UUID userZoneId
    ){
        userZoneService.delete(floorId, userZoneId);
        return ResponseEntity.noContent().build();
    }
}
