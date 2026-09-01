package com.saferoute.domain.evacuation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.entity.DensityUnit;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationDetailResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationSummaryResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.global.api.error.EvacuationErrorCode;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
        controllers = RouteRecalculationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class RouteRecalculationControllerTest {

    private static final String MANAGER_EMAIL = "manager@saferoute.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteRecalculationService routeRecalculationService;

    private final UUID recalculationId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    private RequestPostProcessor asManager() {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(MANAGER_EMAIL, null));
            return request;
        };
    }

    private RouteRecalculationResponse response(RecalculationStatus status) {
        return new RouteRecalculationResponse(
                recalculationId, sessionId, UUID.randomUUID(), CongestionLevel.CROWDED,
                List.of(UUID.randomUUID()), 12.5, status, Instant.now(), Instant.now(), MANAGER_EMAIL, null);
    }

    private RouteRecalculationSummaryResponse summary(RecalculationStatus status) {
        return new RouteRecalculationSummaryResponse(
                recalculationId, sessionId, UUID.randomUUID(), 1, "1층", "CCTV_001", UUID.randomUUID(),
                RecalculationTriggerType.STARTED, CongestionLevel.CROWDED, 3.5,
                DensityUnit.PERSON_PER_SQUARE_METER, status, Instant.now());
    }

    private RouteRecalculationDetailResponse detail(RecalculationStatus status) {
        return new RouteRecalculationDetailResponse(
                recalculationId, sessionId, UUID.randomUUID(), 1, "1층", "CCTV_001", UUID.randomUUID(),
                RecalculationTriggerType.STARTED, CongestionLevel.CROWDED, 3.5,
                DensityUnit.PERSON_PER_SQUARE_METER,
                new RouteRecalculationDetailResponse.RouteSegment(List.of(UUID.randomUUID()), 15.0),
                new RouteRecalculationDetailResponse.RouteSegment(List.of(UUID.randomUUID()), 22.0),
                status, Instant.now(), null, null, null, null);
    }

    @Test
    @DisplayName("목록 조회 시 200과 요약 목록을 반환한다")
    void getRecalculations_returnsOk() throws Exception {
        given(routeRecalculationService.getRecalculations(
                sessionId, RecalculationStatus.PENDING, MANAGER_EMAIL))
                .willReturn(List.of(summary(RecalculationStatus.PENDING)));

        mockMvc.perform(get("/api/v1/route-recalculations")
                        .param("trainingSessionId", sessionId.toString())
                        .param("status", "PENDING")
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].recalculationId").value(recalculationId.toString()));
    }

    @Test
    @DisplayName("상태 필터 없이 목록을 조회할 수 있다")
    void getRecalculations_withoutStatusFilter_returnsOk() throws Exception {
        given(routeRecalculationService.getRecalculations(sessionId, null, MANAGER_EMAIL))
                .willReturn(List.of(summary(RecalculationStatus.APPROVED)));

        mockMvc.perform(get("/api/v1/route-recalculations")
                        .param("trainingSessionId", sessionId.toString())
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("상세 조회 시 200과 previousRoute/candidateRoute를 반환한다")
    void getRecalculationDetail_returnsOk() throws Exception {
        given(routeRecalculationService.getRecalculationDetail(recalculationId, MANAGER_EMAIL))
                .willReturn(detail(RecalculationStatus.PENDING));

        mockMvc.perform(get("/api/v1/route-recalculations/{id}", recalculationId)
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.previousRoute.totalWeight").value(15.0))
                .andExpect(jsonPath("$.result.candidateRoute.totalWeight").value(22.0));
    }

    @Test
    @DisplayName("상세 조회에서 존재하지 않으면 404를 반환한다")
    void getRecalculationDetail_returnsNotFoundWhenMissing() throws Exception {
        willThrow(new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND))
                .given(routeRecalculationService)
                .getRecalculationDetail(recalculationId, MANAGER_EMAIL);

        mockMvc.perform(get("/api/v1/route-recalculations/{id}", recalculationId)
                        .with(asManager()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("승인 성공 시 200과 APPROVED 상태를 반환한다")
    void approve_returnsOk() throws Exception {
        given(routeRecalculationService.approve(recalculationId, MANAGER_EMAIL))
                .willReturn(response(RecalculationStatus.APPROVED));

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/approve", recalculationId).with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
    }

    @Test
    @DisplayName("거절 성공 시 200과 REJECTED 상태를 반환한다")
    void reject_returnsOk() throws Exception {
        given(routeRecalculationService.reject(eq(recalculationId), eq(MANAGER_EMAIL), any()))
                .willReturn(response(RecalculationStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/reject", recalculationId).with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
    }

    @Test
    @DisplayName("존재하지 않는 재탐색이면 404를 반환한다")
    void approve_returnsNotFoundWhenMissing() throws Exception {
        willThrow(new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND))
                .given(routeRecalculationService).approve(any(), any());

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/approve", recalculationId).with(asManager()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미 처리된 재탐색이면 409를 반환한다")
    void approve_returnsConflictWhenAlreadyResolved() throws Exception {
        willThrow(new ApiException(EvacuationErrorCode.INVALID_RECALCULATION_STATUS_TRANSITION))
                .given(routeRecalculationService).approve(any(), any());

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/approve", recalculationId).with(asManager()))
                .andExpect(status().isConflict());
    }
}
