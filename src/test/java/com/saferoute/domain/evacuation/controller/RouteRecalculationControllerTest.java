package com.saferoute.domain.evacuation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RouteRecalculationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class RouteRecalculationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteRecalculationService routeRecalculationService;

    private final UUID recalculationId = UUID.randomUUID();

    private RouteRecalculationResponse response(RecalculationStatus status) {
        return new RouteRecalculationResponse(
                recalculationId, UUID.randomUUID(), UUID.randomUUID(), CongestionLevel.HIGH,
                List.of(UUID.randomUUID()), 12.5, status, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("승인 성공 시 200과 APPROVED 상태를 반환한다")
    void approve_returnsOk() throws Exception {
        given(routeRecalculationService.approve(recalculationId)).willReturn(response(RecalculationStatus.APPROVED));

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/approve", recalculationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
    }

    @Test
    @DisplayName("거절 성공 시 200과 REJECTED 상태를 반환한다")
    void reject_returnsOk() throws Exception {
        given(routeRecalculationService.reject(recalculationId)).willReturn(response(RecalculationStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/reject", recalculationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
    }

    @Test
    @DisplayName("존재하지 않는 재탐색이면 404를 반환한다")
    void approve_returnsNotFoundWhenMissing() throws Exception {
        willThrow(new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND))
                .given(routeRecalculationService).approve(any());

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/approve", recalculationId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미 처리된 재탐색이면 409를 반환한다")
    void approve_returnsConflictWhenAlreadyResolved() throws Exception {
        willThrow(new ApiException(EvacuationErrorCode.INVALID_RECALCULATION_STATUS_TRANSITION))
                .given(routeRecalculationService).approve(any());

        mockMvc.perform(patch("/api/v1/route-recalculations/{id}/approve", recalculationId))
                .andExpect(status().isConflict());
    }
}
