package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.ChangeLightDirectionRequest;
import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdatePiEndpointRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.dto.response.LightDirectionResponse;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.IoTLightSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IoT 유도등", description = "IoT 유도등 등록/조회/방향 제어 API")
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
            @RequestParam(required = false) UUID floorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_LIST_FOUND,
                        iotLightService.getLights(floorId, authentication.getName())));
    }

    @GetMapping("/{lightId}")
    public ResponseEntity<ApiResponse<IoTLightResponse>> getLight(
            @PathVariable UUID lightId,
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DETAIL_FOUND,
                        iotLightService.getLight(lightId, authentication.getName())));
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

    @PatchMapping("/{lightId}/direction")
    public ResponseEntity<ApiResponse<LightDirectionResponse>> changeDirection(
            @PathVariable UUID lightId,
            @Valid @RequestBody ChangeLightDirectionRequest request
    ) {
        LightDirectionResponse response = iotLightService.changeDirection(lightId, request);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DIRECTION_CHANGED, response));
    }

    @PatchMapping("/{lightId}/pi-endpoint")
    public ResponseEntity<ApiResponse<IoTLightResponse>> updatePiEndpoint(
            @PathVariable UUID lightId,
            @Valid @RequestBody UpdatePiEndpointRequest request
    ) {
        IoTLightResponse response = iotLightService.updatePiEndpoint(lightId, request);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_PI_ENDPOINT_UPDATED, response));
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

    @DeleteMapping("/{lightId}")
    public ResponseEntity<ApiResponse<Void>> deleteLight(@PathVariable UUID lightId) {
        iotLightService.deleteLight(lightId);
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DELETED, null));
    }
}
