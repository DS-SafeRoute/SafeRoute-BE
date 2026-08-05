package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.dto.UpdateScenarioRequest;
import com.saferoute.domain.training.service.TrainingScenarioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "훈련 시나리오", description = "훈련 시나리오 등록/조회/수정/삭제 API")
@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class TrainingScenarioController {

    private final TrainingScenarioService scenarioService;

    // GET /api/v1/scenarios
    @GetMapping
    public ResponseEntity<List<ScenarioResponse>> getScenarios() {
        return ResponseEntity.ok(scenarioService.getScenarios());
    }

    // GET /api/v1/scenarios/{scenarioId}
    @GetMapping("/{scenarioId}")
    public ResponseEntity<ScenarioResponse> getScenario(
            @PathVariable UUID scenarioId) {
        return ResponseEntity.ok(scenarioService.getScenario(scenarioId));
    }

    // POST /api/v1/scenarios
    @PostMapping
    public ResponseEntity<ScenarioResponse> createScenario(
            @Valid @RequestBody CreateScenarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scenarioService.createScenario(request));
    }

    // PATCH /api/v1/scenarios/{scenarioId}
    @PatchMapping("/{scenarioId}")
    public ResponseEntity<ScenarioResponse> updateScenario(
            @PathVariable UUID scenarioId,
            @RequestBody UpdateScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.updateScenario(scenarioId, request));
    }

    // DELETE /api/v1/scenarios/{scenarioId}
    @DeleteMapping("/{scenarioId}")
    public ResponseEntity<Void> deleteScenario(
            @PathVariable UUID scenarioId) {
        scenarioService.deleteScenario(scenarioId);
        return ResponseEntity.noContent().build();
    }
}