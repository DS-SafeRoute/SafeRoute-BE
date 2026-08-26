package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.service.TrainingMonitoringService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "훈련 모니터링",
        description = "실행 중인 훈련의 카메라별 최신 주기 캡처 조회 API"
)
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/monitoring")
@RequiredArgsConstructor
public class TrainingMonitoringController {

    private final TrainingMonitoringService trainingMonitoringService;

    @Operation(
            summary = "카메라별 최신 캡처 목록 조회",
            description = """
                    실행 중인 훈련 세션의 건물에 설치된 활성 CCTV 카드 목록을 반환합니다.

                    각 카메라의 thumbnailUrl은 최신 Observation의 monitoringImageKey로 발급한
                    S3 presigned GET URL이며 영구 URL이 아닙니다. capturedAt과 urlExpiresAt은 모두
                    Unix epoch milliseconds 단위입니다.

                    아직 캡처가 없는 카메라도 목록에 포함되며 thumbnailUrl, capturedAt,
                    urlExpiresAt이 모두 null로 반환됩니다. 클라이언트는 이 경우 placeholder를
                    표시해야 합니다. 'n초 전 캡처'와 같은 상대 시간은 capturedAt을 기준으로
                    클라이언트에서 계산합니다.

                    목록은 층 번호 오름차순, 같은 층에서는 CCTV 코드 오름차순으로 정렬됩니다.
                    비활성 CCTV는 반환하지 않습니다. 최신 상태가 필요하면 화면에서 주기적으로
                    이 API를 다시 호출해 주세요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "모니터링 카메라 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "최신 캡처가 있는 카메라",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "TRAINING_SUCCESS_006",
                                                      "message": "모니터링 카메라 목록 조회에 성공했습니다.",
                                                      "result": {
                                                        "sessionId": "d669294e-55e1-4c00-bf67-229d89b76948",
                                                        "cameras": [
                                                          {
                                                            "cctvId": "67b86e33-7874-494c-855f-e591e7847c09",
                                                            "code": "CCTV_001",
                                                            "name": "CAM-1",
                                                            "buildingName": "A동",
                                                            "floorName": "3층",
                                                            "location": "A동 3층",
                                                            "thumbnailUrl": "https://example-bucket.s3.amazonaws.com/training/session/monitoring/CCTV_001/frame.jpg",
                                                            "capturedAt": 1787722095000,
                                                            "urlExpiresAt": 1787725695000
                                                          }
                                                        ]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "캡처가 없는 카메라",
                                            value = """
                                                    {
                                                      "isSuccess": true,
                                                      "code": "TRAINING_SUCCESS_006",
                                                      "message": "모니터링 카메라 목록 조회에 성공했습니다.",
                                                      "result": {
                                                        "sessionId": "d669294e-55e1-4c00-bf67-229d89b76948",
                                                        "cameras": [
                                                          {
                                                            "cctvId": "8b767966-b423-4d1b-bc2a-4107de97ad72",
                                                            "code": "CCTV_002",
                                                            "name": "CAM-2",
                                                            "buildingName": "A동",
                                                            "floorName": "1층",
                                                            "location": "A동 1층",
                                                            "thumbnailUrl": null,
                                                            "capturedAt": null,
                                                            "urlExpiresAt": null
                                                          }
                                                        ]
                                                      }
                                                    }
                                                    """
                                    )
                            }
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "세션이 없거나 요청자와 다른 학교의 세션",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "TRAINING001",
                                      "message": "훈련 세션을 찾을 수 없습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "훈련 세션이 RUNNING 상태가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "TRAINING006",
                                      "message": "진행 중인 훈련 세션을 찾을 수 없습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "S3 presigned GET URL 발급 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "S3_ERROR_003",
                                      "message": "S3 업로드 URL 발급에 실패했습니다."
                                    }
                                    """)
                    )
            )
    })
    @GetMapping("/cameras")
    public ResponseEntity<ApiResponse<MonitoringCameraListResponse>> getCameras(
            @Parameter(
                    description = "조회할 RUNNING 훈련 세션의 UUID",
                    required = true,
                    example = "d669294e-55e1-4c00-bf67-229d89b76948"
            )
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        MonitoringCameraListResponse response =
                trainingMonitoringService.getCameras(sessionId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                TrainingSuccessCode.MONITORING_CAMERA_LIST_FOUND,
                response
        ));
    }
}
