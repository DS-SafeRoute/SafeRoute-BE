package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.TrainingSessionListApiResponse;
import com.saferoute.domain.training.dto.TrainingSessionListResponse;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.service.TrainingSessionService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.TrainingSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "훈련 세션", description = "훈련 세션 생성/시작/종료/조회 API")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class TrainingSessionController {

  private final TrainingSessionService trainingSessionService;

  @Operation(
      summary = "상태별 훈련 세션 목록 조회",
      description = """
              요청자 학교 소속 훈련 세션을 상태로 필터링해 최신 시작 순으로 반환합니다.

              모니터링 화면에 진입할 sessionId를 얻는 용도로 사용합니다.
              예: GET /api/v1/sessions?status=RUNNING

              해당 상태의 세션이 없으면 빈 배열을 반환합니다.
              """
  )
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "훈련 세션 목록 조회 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @io.swagger.v3.oas.annotations.media.Schema(
                  implementation = TrainingSessionListApiResponse.class
              ),
              examples = @ExampleObject(
                  name = "실행 중인 세션 목록",
                  value = """
                          {
                            "isSuccess": true,
                            "code": "TRAINING_SUCCESS_008",
                            "message": "훈련 세션 목록 조회에 성공했습니다.",
                            "result": {
                              "sessions": [
                                {
                                  "sessionId": "d669294e-55e1-4c00-bf67-229d89b76948",
                                  "scenarioName": "3학년 A동 화재 대피 훈련",
                                  "buildingId": "b5a6e5b0-1e3a-4b8a-9b8a-6a2b6b1f5a11",
                                  "buildingName": "A동",
                                  "status": "RUNNING",
                                  "startedAt": "2026-08-26T05:26:00Z"
                                }
                              ]
                            }
                          }
                          """
              )
          )
      ),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "status 값이 올바르지 않거나 누락됨",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = """
                      {
                        "isSuccess": false,
                        "code": "COMMON400",
                        "message": "입력값이 올바르지 않습니다."
                      }
                      """)
          )
      ),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "JWT가 없거나 유효하지 않음",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = """
                      {
                        "isSuccess": false,
                        "code": "COMMON401",
                        "message": "인증이 필요합니다."
                      }
                      """)
          )
      ),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "403",
          description = "MANAGER 권한이 없음",
          content = @Content(
              mediaType = "application/json",
              examples = @ExampleObject(value = """
                      {
                        "isSuccess": false,
                        "code": "COMMON403",
                        "message": "접근 권한이 없습니다."
                      }
                      """)
          )
      )
  })
  @GetMapping
  public ResponseEntity<ApiResponse<TrainingSessionListResponse>> getSessions(
      @Parameter(description = "조회할 세션 상태", required = true, example = "RUNNING")
      @RequestParam TrainingStatus status,
      Authentication authentication
  ) {
    TrainingSessionListResponse response =
        trainingSessionService.getSessions(status, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_SESSION_LIST_FOUND, response));
  }

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
