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

  @Operation(
      summary = "훈련 세션 생성",
      description = """
          scenarioId 시나리오에 대한 훈련 세션을 생성합니다. 시나리오당 세션은 1개만 존재할
          수 있어, 이미 해당 시나리오에 세션이 있으면(과거에 종료된 세션 포함) 생성이 거부됩니다.
          재훈련이 필요하면 세션이 아니라 시나리오를 새로 만들어야 합니다.

          adminId는 요청자와 같은 학교 소속이면서 MANAGER 권한을 가진 사용자여야 합니다.

          status를 RUNNING으로 생성하려면 startedAt을 함께 지정해야 합니다. status를
          SCHEDULED로 생성하는 경우가 일반적인 흐름이며, 이때는 실제 시작 시각이 아직 없으므로
          이후 시작 API(POST /api/v1/sessions/{sessionId}/start) 호출 시 서버가 시작
          시각을 다시 기록합니다.
          """
  )
  @PostMapping("/{scenarioId}")
  public ResponseEntity<TrainingSessionResponse> createTrainingSession(
      @RequestBody CreateSessionRequest request,
      @PathVariable("scenarioId") UUID scenarioId,
      Authentication authentication) {
    return ResponseEntity.ok(trainingSessionService.create(request, scenarioId, authentication.getName()));
  }

  @Operation(
      summary = "훈련 세션 시작",
      description = """
          SCHEDULED 상태의 세션을 RUNNING으로 전이시키고, 시작 시각을 현재 시각으로
          기록합니다. RUNNING이 아닌 다른 상태(SCHEDULED가 아닌 세션)에는 호출할 수 없습니다.

          시작 전에 시나리오의 startNodeId를 기준으로 최초 대피 경로를 계산하며, 시나리오에
          startNodeId가 설정되어 있지 않거나 경로를 찾을 수 없으면 세션 상태를 바꾸지 않고
          요청이 실패합니다. 계산에 성공하면 시나리오 status도 IN_PROGRESS로 함께 전이되고,
          계산된 경로를 따라 건물의 유도등에 대피 방향 안내 명령이 내려갑니다.

          성공 시 웹소켓으로 훈련 상태 변경 이벤트가 발행되므로, 모니터링 화면은 이 이벤트로도
          상태 갱신을 감지할 수 있습니다.
          """
  )
  @PostMapping("/{sessionId}/start")
  public ResponseEntity<ApiResponse<TrainingSessionResponse>> startTrainingSession(
      @PathVariable("sessionId") UUID sessionId,
      Authentication authentication) {
    TrainingSessionResponse response = trainingSessionService.start(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_STARTED, response));
  }

  @Operation(
      summary = "훈련 세션 정상 종료",
      description = """
          RUNNING 상태의 세션을 COMPLETED로 전이시키고 종료 시각을 현재 시각으로 기록합니다.
          RUNNING이 아닌 세션에는 호출할 수 없습니다.

          종료 처리와 함께 시나리오 status는 COMPLETED로 전이되고, 이 시나리오의 화재 셀은
          모두 꺼진 상태로 초기화되며, 이 세션에 대해 대기 중이던 경로 재탐색 요청은 모두
          무효화되고, 건물의 유도등은 평상시 상태로 복구됩니다.

          정상 종료 여부는 관리자가 직접 끝내는 강제 종료(force-end)와 구분됩니다. 정상
          종료는 시나리오를 COMPLETED로, 강제 종료는 ERROR로 남긴다는 점이 다릅니다.
          """
  )
  @PostMapping("/{sessionId}/end")
  public ResponseEntity<ApiResponse<TrainingSessionResponse>> endTrainingSession(
      @PathVariable("sessionId") UUID sessionId,
      Authentication authentication) {
    TrainingSessionResponse response = trainingSessionService.end(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_ENDED, response));
  }

  @Operation(
      summary = "훈련 세션 강제 종료",
      description = """
          관리자가 훈련을 중간에 강제로 중단할 때 사용합니다. RUNNING 상태의 세션을
          STOPPED로 전이시키고 종료 시각을 현재 시각으로 기록합니다. RUNNING이 아닌 세션에는
          호출할 수 없습니다.

          정상 종료(end)와 동일하게 시나리오의 화재 셀 초기화, 대기 중인 경로 재탐색 요청
          무효화, 유도등 평상시 복구가 함께 일어나지만, 시나리오 status는 COMPLETED가 아니라
          ERROR로 표시되어 정상 종료와 구분됩니다.
          """
  )
  @PostMapping("/{sessionId}/force-end")
  public ResponseEntity<ApiResponse<TrainingSessionResponse>> forceEndTrainingSession(
      @PathVariable("sessionId") UUID sessionId,
      Authentication authentication) {
    TrainingSessionResponse response = trainingSessionService.forceEnd(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(TrainingSuccessCode.TRAINING_FORCE_ENDED, response));
  }
}
