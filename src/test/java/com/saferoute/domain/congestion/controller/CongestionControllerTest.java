package com.saferoute.domain.congestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saferoute.domain.congestion.dto.request.ReportCongestionEventRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.domain.congestion.service.CongestionEventImageService;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import com.saferoute.global.security.DeviceAuthenticationFilter;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CongestionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        DeviceAuthenticationFilter.class,
                        SecurityConfig.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class CongestionControllerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CongestionEventService congestionEventService;

    @MockitoBean
    private CongestionEventImageService congestionEventImageService;

    @MockitoBean
    private DeviceAuthorizationService deviceAuthorizationService;

    private ReportCongestionEventRequest validRequest() {
        return new ReportCongestionEventRequest(
                UUID.randomUUID(), UUID.randomUUID(), "CCTV_001",
                CongestionEventType.CONGESTION_STARTED, 2_000L, 9, 4.5,
                CongestionLevel.CROWDED, 1L
        );
    }

    private CongestionEventItem event() {
        return CongestionEventItem.received(
                EVENT_ID, SESSION_ID, "CCTV_001", CongestionEventType.CONGESTION_STARTED,
                2_000L, 9, 4.5, CongestionLevel.CROWDED, 4.5, CongestionLevel.CROWDED, 1L, null
        );
    }

    @Test
    @DisplayName("처음 수신한 eventId이면 이벤트와 201을 반환한다")
    void reportCongestionEvent_returnsCreated() throws Exception {
        given(congestionEventService.reportCongestionEvent(any(), any()))
                .willReturn(IdempotentSaveResult.created(event()));

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.headcount").value(9))
                .andExpect(jsonPath("$.congestionLevel").value("CROWDED"));
    }

    @Test
    @DisplayName("중복 eventId이면 기존 이벤트와 200을 반환한다")
    void reportCongestionEvent_returnsExistingEvent() throws Exception {
        given(congestionEventService.reportCongestionEvent(any(), any()))
                .willReturn(IdempotentSaveResult.existing(event()));

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()));
    }

    @Test
    @DisplayName("trainingSessionId가 UUID 문자열이 아니면 400을 반환한다")
    void reportCongestionEvent_returnsBadRequestWhenTrainingSessionIdIsInvalid() throws Exception {
        ObjectNode body = objectMapper.valueToTree(validRequest());
        body.put("trainingSessionId", "123");

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("trainingSessionId가 없으면 400을 반환한다")
    void reportCongestionEvent_returnsBadRequestWhenTrainingSessionIdIsMissing() throws Exception {
        ObjectNode body = objectMapper.valueToTree(validRequest());
        body.remove("trainingSessionId");

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("eventType이 없으면 400을 반환한다")
    void reportCongestionEvent_returnsBadRequestWhenEventTypeMissing() throws Exception {
        ObjectNode body = objectMapper.valueToTree(validRequest());
        body.remove("eventType");

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("headcount가 음수면 400을 반환한다")
    void reportCongestionEvent_returnsBadRequestWhenHeadcountNegative() throws Exception {
        ObjectNode body = objectMapper.valueToTree(validRequest());
        body.put("headcount", -1);

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("localDensity가 음수면 400을 반환한다")
    void reportCongestionEvent_returnsBadRequestWhenLocalDensityNegative() throws Exception {
        ObjectNode body = objectMapper.valueToTree(validRequest());
        body.put("localDensity", -1.0);

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("configVersion이 0 이하이면 400을 반환한다")
    void reportCongestionEvent_returnsBadRequestWhenConfigVersionNotPositive() throws Exception {
        ObjectNode body = objectMapper.valueToTree(validRequest());
        body.put("configVersion", 0);

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("진행 중인 훈련 세션이 없으면 공통 에러 코드와 409를 반환한다")
    void reportCongestionEvent_returnsConflictWhenRunningSessionMissing() throws Exception {
        given(congestionEventService.reportCongestionEvent(any(), any()))
                .willThrow(new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TRAINING006"));
    }

    @Test
    @DisplayName("감시 면적을 계산할 수 없으면 CONGESTION009와 409를 반환한다")
    void reportCongestionEvent_returnsConflictWhenMonitoredAreaUnavailable() throws Exception {
        given(congestionEventService.reportCongestionEvent(any(), any()))
                .willThrow(new ApiException(CongestionErrorCode.MONITORED_AREA_NOT_AVAILABLE));

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONGESTION009"));
    }

    @Test
    @DisplayName("후속 처리 실패 시 공통 형식의 CONGESTION001과 503을 반환한다")
    void reportCongestionEvent_returnsServiceUnavailableWhenProcessingFails() throws Exception {
        given(congestionEventService.reportCongestionEvent(any(), any()))
                .willThrow(new ApiException(CongestionErrorCode.EVENT_PROCESSING_FAILED));

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CONGESTION001"))
                .andExpect(jsonPath("$.message")
                        .value("혼잡 관측 후속 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("동일 eventId의 식별 정보가 다르면 CONGESTION002와 409를 반환한다")
    void reportCongestionEvent_returnsConflictWhenEventIdentityMismatches() throws Exception {
        given(congestionEventService.reportCongestionEvent(any(), any()))
                .willThrow(new ApiException(CongestionErrorCode.EVENT_IDENTITY_MISMATCH));

        mockMvc.perform(post("/api/v1/device/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CONGESTION002"));
    }

    @Test
    @DisplayName("이벤트 이미지 연결 성공 시 204를 반환한다")
    void connectEventImage_returnsNoContent() throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("eventImageKey", "training/" + SESSION_ID + "/events/CCTV_001/"
                        + EVENT_ID + ".jpg")
                .put("uploadedAt", 1_786_500_002_800L);

        mockMvc.perform(patch("/api/v1/device/congestion-events/{eventId}/image", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        verify(congestionEventImageService).connectImage(any(), any(), any());
    }

    @Test
    @DisplayName("eventImageKey가 없으면 400을 반환한다")
    void connectEventImage_returnsBadRequestWithoutImageKey() throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("uploadedAt", 1_786_500_002_800L);

        mockMvc.perform(patch("/api/v1/device/congestion-events/{eventId}/image", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이벤트를 찾을 수 없으면 CONGESTION003과 404를 반환한다")
    void connectEventImage_returnsNotFoundWhenEventMissing() throws Exception {
        doThrow(new ApiException(CongestionErrorCode.EVENT_NOT_FOUND))
                .when(congestionEventImageService).connectImage(any(), any(), any());

        mockMvc.perform(patch("/api/v1/device/congestion-events/{eventId}/image", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validImageRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CONGESTION003"));
    }

    @Test
    @DisplayName("이미지 상태가 충돌하면 CONGESTION005와 409를 반환한다")
    void connectEventImage_returnsConflictWhenImageStateConflicts() throws Exception {
        doThrow(new ApiException(CongestionErrorCode.EVENT_IMAGE_STATE_CONFLICT))
                .when(congestionEventImageService).connectImage(any(), any(), any());

        mockMvc.perform(patch("/api/v1/device/congestion-events/{eventId}/image", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validImageRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CONGESTION005"));
    }

    private ObjectNode validImageRequest() {
        return objectMapper.createObjectNode()
                .put("eventImageKey", "training/" + SESSION_ID + "/events/CCTV_001/"
                        + EVENT_ID + ".jpg")
                .put("uploadedAt", 1_786_500_002_800L);
    }
}
