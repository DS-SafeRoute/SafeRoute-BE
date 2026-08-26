package com.saferoute.domain.training.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.service.TrainingMonitoringService;
import com.saferoute.global.api.error.S3ErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TrainingMonitoringController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "manager@saferoute.com")
class TrainingMonitoringControllerTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CCTV_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_CCTV_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String EMAIL = "manager@saferoute.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingMonitoringService trainingMonitoringService;

    @Test
    void 카메라별_최신_캡처_목록을_공통_응답으로_반환한다() throws Exception {
        MonitoringCameraResponse firstCamera = new MonitoringCameraResponse(
                CCTV_ID,
                "CCTV_001",
                "CAM-1",
                "A동",
                "3층",
                "A동 3층",
                "https://example.com/frame.jpg",
                1_787_722_095_000L,
                1_787_725_695_000L
        );
        MonitoringCameraResponse secondCamera = new MonitoringCameraResponse(
                SECOND_CCTV_ID,
                "CCTV_002",
                "CAM-2",
                "A동",
                "4층",
                "A동 4층",
                "https://example.com/second-frame.jpg",
                1_787_722_096_000L,
                1_787_725_696_000L
        );
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willReturn(new MonitoringCameraListResponse(
                        SESSION_ID,
                        List.of(firstCamera, secondCamera)
                ));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("TRAINING_SUCCESS_006"))
                .andExpect(jsonPath("$.result.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.cameras[0].cctvId").value(CCTV_ID.toString()))
                .andExpect(jsonPath("$.result.cameras[0].code").value("CCTV_001"))
                .andExpect(jsonPath("$.result.cameras[0].floorName").value("3층"))
                .andExpect(jsonPath("$.result.cameras[0].thumbnailUrl")
                        .value("https://example.com/frame.jpg"))
                .andExpect(jsonPath("$.result.cameras[0].capturedAt")
                        .value(1_787_722_095_000L))
                .andExpect(jsonPath("$.result.cameras[0].urlExpiresAt")
                        .value(1_787_725_695_000L))
                .andExpect(jsonPath("$.result.cameras[1].cctvId")
                        .value(SECOND_CCTV_ID.toString()))
                .andExpect(jsonPath("$.result.cameras[1].code").value("CCTV_002"))
                .andExpect(jsonPath("$.result.cameras[1].thumbnailUrl")
                        .value("https://example.com/second-frame.jpg"))
                .andExpect(jsonPath("$.result.cameras[1].capturedAt")
                        .value(1_787_722_096_000L));
    }

    @Test
    void 일부_CCTV에만_캡처가_있어도_전체_목록과_null_이미지_필드를_반환한다() throws Exception {
        MonitoringCameraResponse capturedCamera = new MonitoringCameraResponse(
                CCTV_ID,
                "CCTV_001",
                "CAM-1",
                "A동",
                "1층",
                "A동 1층",
                "https://example.com/frame.jpg",
                1_787_722_095_000L,
                1_787_725_695_000L
        );
        MonitoringCameraResponse pendingCamera = new MonitoringCameraResponse(
                SECOND_CCTV_ID,
                "CCTV_002",
                "CAM-2",
                "A동",
                "2층",
                "A동 2층",
                null,
                null,
                null
        );
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willReturn(new MonitoringCameraListResponse(
                        SESSION_ID,
                        List.of(capturedCamera, pendingCamera)
                ));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.cameras.length()").value(2))
                .andExpect(jsonPath("$.result.cameras[0].thumbnailUrl")
                        .value("https://example.com/frame.jpg"))
                .andExpect(jsonPath("$.result.cameras[1].code").value("CCTV_002"))
                .andExpect(jsonPath("$.result.cameras[1].thumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.result.cameras[1].capturedAt").isEmpty())
                .andExpect(jsonPath("$.result.cameras[1].urlExpiresAt").isEmpty());
    }

    @Test
    void 캡처가_없는_카메라는_이미지_필드를_null로_반환한다() throws Exception {
        MonitoringCameraResponse camera = new MonitoringCameraResponse(
                CCTV_ID,
                "CCTV_001",
                "CAM-1",
                "A동",
                "1층",
                "A동 1층",
                null,
                null,
                null
        );
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willReturn(new MonitoringCameraListResponse(SESSION_ID, List.of(camera)));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.cameras[0].thumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.result.cameras[0].capturedAt").isEmpty())
                .andExpect(jsonPath("$.result.cameras[0].urlExpiresAt").isEmpty());
    }

    @Test
    void 세션을_찾을_수_없으면_404를_반환한다() throws Exception {
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willThrow(new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAINING001"));
    }

    @Test
    void 다른_학교의_세션이면_존재_여부를_노출하지_않고_404를_반환한다() throws Exception {
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willThrow(new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TRAINING001"))
                .andExpect(jsonPath("$.message").value("훈련 세션을 찾을 수 없습니다."));
    }

    @Test
    void 실행_중인_세션이_아니면_409를_반환한다() throws Exception {
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willThrow(new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRAINING006"));
    }

    @Test
    void S3_조회_URL_발급에_실패하면_500과_정의된_오류_응답을_반환한다() throws Exception {
        given(trainingMonitoringService.getCameras(SESSION_ID, EMAIL))
                .willThrow(new ApiException(S3ErrorCode.PRESIGNED_GET_URL_GENERATION_FAILED));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/monitoring/cameras", SESSION_ID)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("S3_ERROR_005"))
                .andExpect(jsonPath("$.message")
                        .value("S3 이미지 조회 URL 발급에 실패했습니다."));
    }
}
