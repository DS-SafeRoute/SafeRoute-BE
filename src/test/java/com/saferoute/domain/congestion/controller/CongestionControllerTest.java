package com.saferoute.domain.congestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.UUID;
import java.util.Optional;
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
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class CongestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CongestionEventService congestionEventService;

    private ReportCongestionRequest validRequest() {
        return new ReportCongestionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "CCTV_001", 5.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, 1L, null
        );
    }

    @Test
    @DisplayName("처음 수신한 eventId이면 관측값과 201을 반환한다")
    void reportCongestion_returnsCreated() throws Exception {
        given(congestionEventService.reportCongestion(any()))
                .willReturn(Optional.of(IdempotentSaveResult.created(observation())));

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("observation-1"))
                .andExpect(jsonPath("$.avgHeadcount").value(5.0));
    }

    @Test
    @DisplayName("중복 eventId이면 기존 관측값과 200을 반환한다")
    void reportCongestion_returnsExistingObservation() throws Exception {
        given(congestionEventService.reportCongestion(any()))
                .willReturn(Optional.of(IdempotentSaveResult.existing(observation())));

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("observation-1"));
    }

    @Test
    @DisplayName("edgeId가 없으면 400을 반환한다")
    void reportCongestion_returnsBadRequestWhenEdgeIdMissing() throws Exception {
        ReportCongestionRequest invalid = new ReportCongestionRequest(
                UUID.randomUUID(), null, "CCTV_001", 5.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, 1L, null
        );

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("avgHeadcount가 음수면 400을 반환한다")
    void reportCongestion_returnsBadRequestWhenAvgHeadcountNegative() throws Exception {
        ReportCongestionRequest invalid = new ReportCongestionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "CCTV_001", -1.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, 1L, null
        );

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("peakHeadcount가 음수면 400을 반환한다")
    void reportCongestion_returnsBadRequestWhenPeakHeadcountNegative() throws Exception {
        ReportCongestionRequest invalid = new ReportCongestionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "CCTV_001", 5.0, -1, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, 1L, null
        );

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("avgHeadcount가 peakHeadcount보다 크면 400을 반환한다")
    void reportCongestion_returnsBadRequestWhenAvgExceedsPeak() throws Exception {
        ReportCongestionRequest invalid = new ReportCongestionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "CCTV_001", 9.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, 1L, null
        );

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("windowStart가 windowEnd보다 이후이면 400을 반환한다")
    void reportCongestion_returnsBadRequestWhenWindowStartAfterWindowEnd() throws Exception {
        ReportCongestionRequest invalid = new ReportCongestionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "CCTV_001", 5.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 2_000L, 1_000L, 2_000L, 1L, null
        );

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("서비스가 MAP_EDGE_NOT_FOUND를 던지면 404를 반환한다")
    void reportCongestion_returnsNotFoundWhenEdgeMissing() throws Exception {
        given(congestionEventService.reportCongestion(any()))
                .willThrow(new ApiException(EvacuationErrorCode.MAP_EDGE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    private ObservationItem observation() {
        return ObservationItem.create(
                "observation-1", "session-1", "CCTV_001", 5.0, 8, 25,
                2.5, CongestionLevel.CROWDED, 1_000L, 2_000L,
                2_000L, null, 1L
        );
    }
}
