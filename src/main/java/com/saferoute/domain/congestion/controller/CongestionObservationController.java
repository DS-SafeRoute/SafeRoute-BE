package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.ReportObservationRequest;
import com.saferoute.domain.congestion.dto.response.ObservationResponse;
import com.saferoute.domain.congestion.service.CongestionObservationService;
import com.saferoute.domain.device.entity.Cctv;
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

@Tag(name = "혼잡도", description = "Pi 5초 관측값 수신 API")
@RestController
@RequestMapping("/api/v1/device/congestion-observations")
@RequiredArgsConstructor
public class CongestionObservationController {

    private final DeviceAuthorizationService deviceAuthorizationService;
    private final CongestionObservationService congestionObservationService;

    @PostMapping
    public ResponseEntity<ObservationResponse> reportObservation(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody ReportObservationRequest request
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, request.cctvCode());
        IdempotentSaveResult<ObservationItem> saveResult =
                congestionObservationService.reportObservation(cctv, request);
        ObservationResponse response = ObservationResponse.from(saveResult.item());
        HttpStatus status = saveResult.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
