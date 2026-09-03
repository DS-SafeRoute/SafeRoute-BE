package com.saferoute.domain.training.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.training.dto.TrainingSessionListResponse;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.dto.TrainingSessionSummaryResponse;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.service.TrainingSessionService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// IoTLightControllerTest와 동일한 이유로 JwtAuthenticationFilter/SecurityConfig를 슬라이스에서 제외한다.
@WebMvcTest(
        controllers = TrainingSessionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "manager@saferoute.com")
class TrainingSessionControllerTest {

    private static final String EMAIL = "manager@saferoute.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingSessionService trainingSessionService;

    private final UUID sessionId = UUID.randomUUID();

    private TrainingSessionResponse sampleResponse(TrainingStatus status) {
        return TrainingSessionResponse.builder()
                .status(status)
                .startedAt(Instant.now())
                .adminName("박현지")
                .scenarioName("정기 훈련")
                .build();
    }

    // === getSessions ===

    @Test
    @DisplayName("GET /sessions?status=SCHEDULED - 세션 목록에 scenarioId를 포함한다")
    void getSessions_success() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        TrainingSessionSummaryResponse summary = new TrainingSessionSummaryResponse(
                sessionId,
                scenarioId,
                "3학년 A동 화재 대피 훈련",
                buildingId,
                "A동",
                TrainingStatus.SCHEDULED,
                null
        );
        given(trainingSessionService.getSessions(TrainingStatus.SCHEDULED, EMAIL))
                .willReturn(new TrainingSessionListResponse(List.of(summary)));

        mockMvc.perform(get("/api/v1/sessions")
                        .param("status", "SCHEDULED")
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("TRAINING_SUCCESS_008"))
                .andExpect(jsonPath("$.result.sessions[0].sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.result.sessions[0].scenarioId").value(scenarioId.toString()))
                .andExpect(jsonPath("$.result.sessions[0].scenarioName").value("3학년 A동 화재 대피 훈련"))
                .andExpect(jsonPath("$.result.sessions[0].buildingId").value(buildingId.toString()))
                .andExpect(jsonPath("$.result.sessions[0].buildingName").value("A동"))
                .andExpect(jsonPath("$.result.sessions[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.result.sessions[0].startedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("\"startedAt\":null")));
    }

    @Test
    @DisplayName("GET /sessions?status=RUNNING - 해당 상태의 세션이 없으면 빈 배열을 반환한다")
    void getSessions_empty_returnsEmptyArray() throws Exception {
        given(trainingSessionService.getSessions(TrainingStatus.RUNNING, EMAIL))
                .willReturn(new TrainingSessionListResponse(List.of()));

        mockMvc.perform(get("/api/v1/sessions")
                        .param("status", "RUNNING")
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessions").isEmpty());
    }

    @Test
    @DisplayName("GET /sessions - status 파라미터가 없으면 400을 반환한다")
    void getSessions_missingStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    @DisplayName("GET /sessions - status 값이 올바르지 않으면 400을 반환한다")
    void getSessions_invalidStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")
                        .param("status", "NOT_A_STATUS")
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    // === create ===

    @Test
    @DisplayName("POST /sessions/{scenarioId} - 이전 status와 startedAt을 보내도 SCHEDULED 세션을 생성한다")
    void createTrainingSession_ignoresLegacyStatusAndStartedAt() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        TrainingSessionResponse response = TrainingSessionResponse.builder()
                .id(sessionId)
                .status(TrainingStatus.SCHEDULED)
                .startedAt(null)
                .adminName("박현지")
                .scenarioName("정기 훈련")
                .build();
        given(trainingSessionService.create(
                argThat(request -> adminId.equals(request.getAdminId())),
                eq(scenarioId),
                eq(EMAIL)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/sessions/{scenarioId}", scenarioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adminId": "%s",
                                  "status": "RUNNING",
                                  "startedAt": "2026-09-03T10:00:00Z"
                                }
                                """.formatted(adminId))
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.startedAt").doesNotExist());
    }

    @Test
    @DisplayName("POST /sessions/{scenarioId} - adminId가 없으면 400을 반환한다")
    void createTrainingSession_missingAdminId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{scenarioId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    // === start ===

    @Test
    @DisplayName("POST /sessions/{sessionId}/start - 훈련을 시작하면 200을 반환한다")
    void startTrainingSession_success() throws Exception {
        given(trainingSessionService.start(sessionId, EMAIL)).willReturn(sampleResponse(TrainingStatus.RUNNING));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/start", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.status").value("RUNNING"));
    }

    @Test
    @DisplayName("POST /sessions/{sessionId}/start - SCHEDULED가 아닌 세션이면 409를 반환한다")
    void startTrainingSession_invalidTransition_returnsConflict() throws Exception {
        given(trainingSessionService.start(eq(sessionId), eq(EMAIL)))
                .willThrow(new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/start", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /sessions/{sessionId}/start - 세션이 없으면 404를 반환한다")
    void startTrainingSession_notFound_returns404() throws Exception {
        given(trainingSessionService.start(eq(sessionId), eq(EMAIL)))
                .willThrow(new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/start", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isNotFound());
    }

    // === end ===

    @Test
    @DisplayName("POST /sessions/{sessionId}/end - 정상 종료하면 200을 반환하고 endedAt을 포함한다")
    void endTrainingSession_success() throws Exception {
        Instant endedAt = Instant.parse("2026-09-03T10:00:00Z");
        TrainingSessionResponse response = TrainingSessionResponse.builder()
                .status(TrainingStatus.COMPLETED)
                .startedAt(Instant.parse("2026-09-03T09:00:00Z"))
                .endedAt(endedAt)
                .adminName("박현지")
                .scenarioName("정기 훈련")
                .build();
        given(trainingSessionService.end(sessionId, EMAIL)).willReturn(response);

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/end", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.endedAt").value(endedAt.toString()));
    }

    @Test
    @DisplayName("POST /sessions/{sessionId}/end - RUNNING이 아닌 세션이면 409를 반환한다")
    void endTrainingSession_invalidTransition_returnsConflict() throws Exception {
        given(trainingSessionService.end(eq(sessionId), eq(EMAIL)))
                .willThrow(new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/end", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isConflict());
    }

    // === force-end ===

    @Test
    @DisplayName("POST /sessions/{sessionId}/force-end - 강제 종료하면 200을 반환한다")
    void forceEndTrainingSession_success() throws Exception {
        given(trainingSessionService.forceEnd(sessionId, EMAIL)).willReturn(sampleResponse(TrainingStatus.STOPPED));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/force-end", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("STOPPED"));
    }

    @Test
    @DisplayName("POST /sessions/{sessionId}/force-end - RUNNING이 아닌 세션이면 409를 반환한다")
    void forceEndTrainingSession_invalidTransition_returnsConflict() throws Exception {
        given(trainingSessionService.forceEnd(eq(sessionId), eq(EMAIL)))
                .willThrow(new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/force-end", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isConflict());
    }
}
