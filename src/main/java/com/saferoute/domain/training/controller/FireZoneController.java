package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateFireZoneRequest;
import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.service.FireZoneService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "화재구역", description = "훈련 시나리오 발화점 지정 API")
@RestController
@RequestMapping("/api/v1/scenarios/{scenarioId}/fire-zones")
@RequiredArgsConstructor
public class FireZoneController {

    private final FireZoneService fireZoneService;

    @PostMapping
    public ResponseEntity<FireZoneResponse> designateOrigin(
            @PathVariable UUID scenarioId,
            @Valid @RequestBody CreateFireZoneRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fireZoneService.designateOrigin(scenarioId, request, authentication.getName()));
    }
}
