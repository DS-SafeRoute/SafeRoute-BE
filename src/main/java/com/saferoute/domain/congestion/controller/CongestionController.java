package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.ReportCongestionEventRequest;
import com.saferoute.domain.congestion.dto.request.ConnectEventImageRequest;
import com.saferoute.domain.congestion.dto.response.CongestionEventResponse;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.domain.congestion.service.CongestionEventImageService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@Tag(name = "혼잡도", description = "Pi 즉시 혼잡 이벤트 수신 API")
@RestController
@RequestMapping("/api/v1/device/congestion-events")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionEventService congestionEventService;
    private final CongestionEventImageService congestionEventImageService;
    private final DeviceAuthorizationService deviceAuthorizationService;

    @PostMapping
    public ResponseEntity<CongestionEventResponse> reportCongestionEvent(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody ReportCongestionEventRequest request
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, request.cctvCode());
        IdempotentSaveResult<CongestionEventItem> saveResult =
                congestionEventService.reportCongestionEvent(cctv, request);
        CongestionEventResponse response = CongestionEventResponse.from(saveResult.item());
        HttpStatus status = saveResult.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @PatchMapping("/{eventId}/image")
    public ResponseEntity<Void> connectEventImage(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable UUID eventId,
            @Valid @RequestBody ConnectEventImageRequest request
    ) {
        congestionEventImageService.connectImage(principal, eventId, request);
        return ResponseEntity.noContent().build();
    }
}
