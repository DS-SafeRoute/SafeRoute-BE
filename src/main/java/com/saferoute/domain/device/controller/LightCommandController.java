package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.AckLightCommandRequest;
import com.saferoute.domain.device.dto.response.LightCommandAckResponse;
import com.saferoute.domain.device.dto.response.LightCommandListResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.device.service.LightCommandService;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유도등 명령", description = "Pi 유도등 명령 폴링/ACK API")
@RestController
@RequestMapping("/api/v1/device/light-commands")
@RequiredArgsConstructor
@Validated
public class LightCommandController {

    private final DeviceAuthorizationService deviceAuthorizationService;
    private final LightCommandService lightCommandService;

    @GetMapping
    public ResponseEntity<LightCommandListResponse> pollCommands(
            @AuthenticationPrincipal DevicePrincipal principal,
            @RequestParam @NotBlank String cctvCode
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, cctvCode);
        return ResponseEntity.ok(lightCommandService.pollCommands(cctv));
    }

    @PatchMapping("/{commandId}/ack")
    public ResponseEntity<LightCommandAckResponse> ack(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable UUID commandId,
            @Valid @RequestBody AckLightCommandRequest request
    ) {
        return ResponseEntity.ok(lightCommandService.ack(commandId, principal, request));
    }
}
