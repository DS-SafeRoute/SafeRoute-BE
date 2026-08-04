package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.IoTLightSuccessCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lights")
@RequiredArgsConstructor
public class IoTLightController {

    private final IoTLightService iotLightService;

    @PostMapping
    public ResponseEntity<ApiResponse<IoTLightResponse>> createLight(
            @Valid @RequestBody CreateIoTLightRequest request
    ) {
        IoTLightResponse response = iotLightService.createLight(request);
        return ResponseEntity.status(IoTLightSuccessCode.IOT_LIGHT_CREATED.getHttpStatus())
                .body(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_CREATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<IoTLightResponse>>> getLights(
            @RequestParam(required = false) UUID floorId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_LIST_FOUND, iotLightService.getLights(floorId)));
    }

    @GetMapping("/{lightId}")
    public ResponseEntity<ApiResponse<IoTLightResponse>> getLight(@PathVariable UUID lightId) {
        return ResponseEntity.ok(
                ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DETAIL_FOUND, iotLightService.getLight(lightId)));
    }

    @PatchMapping("/{lightId}/guidance")
    public ResponseEntity<ApiResponse<IoTLightResponse>> configureGuidance(
            @PathVariable UUID lightId,
            @Valid @RequestBody ConfigureGuidanceRequest request
    ) {
        IoTLightResponse response = iotLightService.configureGuidance(lightId, request);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_GUIDANCE_CONFIGURED, response));
    }

    @PatchMapping("/{lightId}")
    public ResponseEntity<ApiResponse<IoTLightResponse>> updateLight(
            @PathVariable UUID lightId,
            @Valid @RequestBody UpdateIoTLightRequest request
    ) {
        IoTLightResponse response = iotLightService.updateLight(lightId, request);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_UPDATED, response));
    }

    @PatchMapping("/{lightId}/enable")
    public ResponseEntity<ApiResponse<IoTLightResponse>> enableLight(@PathVariable UUID lightId) {
        IoTLightResponse response = iotLightService.enableLight(lightId);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_ENABLED, response));
    }

    @PatchMapping("/{lightId}/disable")
    public ResponseEntity<ApiResponse<IoTLightResponse>> disableLight(@PathVariable UUID lightId) {
        IoTLightResponse response = iotLightService.disableLight(lightId);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DISABLED, response));
    }
}
