package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.response.CongestionConfigQueryResponse;
import com.saferoute.domain.congestion.service.CongestionConfigQueryService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혼잡도", description = "Pi 혼잡 설정 조회 API")
@RestController
@RequestMapping("/api/v1/device/congestion-config")
@RequiredArgsConstructor
@Validated
public class CongestionConfigController {

    private final DeviceAuthorizationService deviceAuthorizationService;
    private final CongestionConfigQueryService congestionConfigQueryService;

    @GetMapping
    public ResponseEntity<CongestionConfigQueryResponse> getConfig(
            @AuthenticationPrincipal DevicePrincipal principal,
            @RequestParam @NotBlank String cctvCode
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, cctvCode);
        return ResponseEntity.ok(congestionConfigQueryService.getConfigFor(cctv));
    }
}
