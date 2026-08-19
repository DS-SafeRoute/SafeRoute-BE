package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.dto.response.ObservationResponse;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혼잡도", description = "CCTV 혼잡 이벤트 수신 API")
@RestController
@RequestMapping("/api/v1/device/congestion-events")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionEventService congestionEventService;
    private final DeviceAuthorizationService deviceAuthorizationService;

    @PostMapping
    public ResponseEntity<ObservationResponse> reportCongestion(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody ReportCongestionRequest request
    ) {
        deviceAuthorizationService.validateCctv(principal, request.cctvCode());
        IdempotentSaveResult<ObservationItem> saveResult = congestionEventService.reportCongestion(request);
        ObservationResponse response = ObservationResponse.from(saveResult.item());
        HttpStatus status = saveResult.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
