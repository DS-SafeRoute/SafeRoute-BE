package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.training.service.TrainingSessionService;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class TrainingSessionController {

  private final TrainingSessionService trainingSessionService;

  @PostMapping("/{scenarioId}")
  public ResponseEntity<TrainingSessionResponse> createTrainingSession(
      @RequestBody CreateSessionRequest request,
      @PathVariable("scenarioId") UUID scenarioId) {
    return ResponseEntity.ok(trainingSessionService.create(request, scenarioId));
  }
}
