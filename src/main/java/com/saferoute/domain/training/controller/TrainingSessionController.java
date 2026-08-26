package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.service.TrainingSessionService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.TrainingSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "훈련 세션", description = "훈련 세션 생성/시작/종료 API")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class TrainingSessionController {

  private final TrainingSessionService trainingSessionService;

  @PostMapping("/{scenarioId}")
  public ResponseEntity<TrainingSessionResponse> createTrainingSession(
      @RequestBody CreateSessionRequest request,
      @PathVariable("scenarioId") UUID scenarioId,
      Authentication authentication) {
    return ResponseEntity.ok(trainingSessionService.create(request, scenarioId, authentication.getName()));
  }

  @PostMapping("/{sessionId}/start")
  public ResponseEntity<ApiResponse<TrainingSessionResponse>> startTrainingSession(
      @PathVariable("sessionId") UUID sessionId,
      Authentication authentication) {
    TrainingSessionResponse response = trainingSessionService.start(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_STARTED, response));
  }

  @PostMapping("/{sessionId}/end")
  public ResponseEntity<ApiResponse<TrainingSessionResponse>> endTrainingSession(
      @PathVariable("sessionId") UUID sessionId,
      Authentication authentication) {
    TrainingSessionResponse response = trainingSessionService.end(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_ENDED, response));
  }

  @PostMapping("/{sessionId}/force-end")
  public ResponseEntity<ApiResponse<TrainingSessionResponse>> forceEndTrainingSession(
      @PathVariable("sessionId") UUID sessionId,
      Authentication authentication) {
    TrainingSessionResponse response = trainingSessionService.forceEnd(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_FORCE_ENDED, response));
  }
}
