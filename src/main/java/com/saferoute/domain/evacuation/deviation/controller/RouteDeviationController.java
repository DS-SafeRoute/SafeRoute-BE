package com.saferoute.domain.evacuation.deviation.controller;

import com.saferoute.domain.evacuation.deviation.dto.RouteDeviationResponse;
import com.saferoute.domain.evacuation.deviation.service.RouteDeviationService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "경로 이탈률", description = "유도등 안내 방향과 CCTV 탐지 결과를 비교한 경로 이탈률 조회 API")
@RestController
@RequestMapping("/api/v1/lights/{lightId}/deviation")
@RequiredArgsConstructor
public class RouteDeviationController {

    private final RouteDeviationService routeDeviationService;

    @GetMapping
    public ResponseEntity<ApiResponse<RouteDeviationResponse>> getDeviationRate(
            @PathVariable UUID lightId,
            @RequestParam UUID trainingSessionId,
            Authentication authentication
    ) {
        RouteDeviationResponse response =
                routeDeviationService.calculate(lightId, trainingSessionId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
