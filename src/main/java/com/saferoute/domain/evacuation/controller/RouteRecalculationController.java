package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.EvacuationSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "재탐색 승인", description = "혼잡 재탐색 결과 승인/거절 API")
@RestController
@RequestMapping("/api/v1/route-recalculations")
@RequiredArgsConstructor
public class RouteRecalculationController {

    private final RouteRecalculationService routeRecalculationService;

    @PatchMapping("/{recalculationId}/approve")
    public ResponseEntity<ApiResponse<RouteRecalculationResponse>> approve(@PathVariable UUID recalculationId) {
        RouteRecalculationResponse response = routeRecalculationService.approve(recalculationId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_APPROVED, response));
    }

    @PatchMapping("/{recalculationId}/reject")
    public ResponseEntity<ApiResponse<RouteRecalculationResponse>> reject(@PathVariable UUID recalculationId) {
        RouteRecalculationResponse response = routeRecalculationService.reject(recalculationId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_REJECTED, response));
    }
}
