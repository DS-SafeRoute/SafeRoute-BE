package com.saferoute.domain.congestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
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
                UUID.randomUUID(), "CCTV_001", 5, 8, CongestionLevel.HIGH, 1000L, 2000L, null);
    }

    @Test
    @DisplayName("유효한 요청이면 200을 반환한다")
    void reportCongestion_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("edgeId가 없으면 400을 반환한다")
    void reportCongestion_returnsBadRequestWhenEdgeIdMissing() throws Exception {
        ReportCongestionRequest invalid = new ReportCongestionRequest(
                null, "CCTV_001", 5, 8, CongestionLevel.HIGH, 1000L, 2000L, null);

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("서비스가 MAP_EDGE_NOT_FOUND를 던지면 404를 반환한다")
    void reportCongestion_returnsNotFoundWhenEdgeMissing() throws Exception {
        willThrow(new ApiException(EvacuationErrorCode.MAP_EDGE_NOT_FOUND))
                .given(congestionEventService).reportCongestion(any());

        mockMvc.perform(post("/api/v1/congestion-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }
}
