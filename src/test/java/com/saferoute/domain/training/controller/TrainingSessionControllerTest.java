package com.saferoute.domain.training.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.service.TrainingSessionService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("POST /sessions/{sessionId}/end - 정상 종료하면 200을 반환한다")
    void endTrainingSession_success() throws Exception {
        given(trainingSessionService.end(sessionId, EMAIL)).willReturn(sampleResponse(TrainingStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/end", sessionId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
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
