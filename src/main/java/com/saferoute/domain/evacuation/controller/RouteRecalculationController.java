package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.recalculation.dto.request.RejectRouteRecalculationRequest;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationDetailResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationSummaryResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.EvacuationSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "재탐색 승인", description = "혼잡 재탐색 결과 조회/승인/거절 API")
@RestController
@RequestMapping("/api/v1/route-recalculations")
@RequiredArgsConstructor
public class RouteRecalculationController {

    private final RouteRecalculationService routeRecalculationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteRecalculationSummaryResponse>>> getRecalculations(
            @RequestParam UUID trainingSessionId,
            @RequestParam(required = false) RecalculationStatus status
    ) {
        List<RouteRecalculationSummaryResponse> response =
                routeRecalculationService.getRecalculations(trainingSessionId, status);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_LIST_FOUND, response));
    }

    @GetMapping("/{recalculationId}")
    public ResponseEntity<ApiResponse<RouteRecalculationDetailResponse>> getRecalculationDetail(
            @PathVariable UUID recalculationId
    ) {
        RouteRecalculationDetailResponse response = routeRecalculationService.getRecalculationDetail(recalculationId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_DETAIL_FOUND, response));
    }

    @PatchMapping("/{recalculationId}/approve")
    public ResponseEntity<ApiResponse<RouteRecalculationResponse>> approve(
            @PathVariable UUID recalculationId,
            Authentication authentication
    ) {
        RouteRecalculationResponse response =
                routeRecalculationService.approve(recalculationId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_APPROVED, response));
    }

    @PatchMapping("/{recalculationId}/reject")
    public ResponseEntity<ApiResponse<RouteRecalculationResponse>> reject(
            @PathVariable UUID recalculationId,
            Authentication authentication,
            @RequestBody(required = false) RejectRouteRecalculationRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        RouteRecalculationResponse response =
                routeRecalculationService.reject(recalculationId, authentication.getName(), reason);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_REJECTED, response));
    }
}
