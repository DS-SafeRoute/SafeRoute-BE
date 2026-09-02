package com.saferoute.domain.congestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.congestion.dto.response.CongestionConfigQueryResponse;
import com.saferoute.domain.congestion.dto.response.CongestionThresholdsResponse;
import com.saferoute.domain.congestion.dto.response.EventDetectionResponse;
import com.saferoute.domain.congestion.service.CongestionConfigQueryService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.DeviceAuthenticationFilter;
import com.saferoute.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CongestionConfigController.class,
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
class CongestionConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceAuthorizationService deviceAuthorizationService;

    @MockitoBean
    private CongestionConfigQueryService congestionConfigQueryService;

    @Test
    @DisplayName("cctvCode 없이 요청하면 400을 반환한다")
    void getConfig_missingCctvCode_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/device/congestion-config"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("훈련 중이면 설정 값을 포함한 응답을 반환한다")
    void getConfig_trainingActive_returnsFullConfig() throws Exception {
        given(deviceAuthorizationService.validateCctv(any(), org.mockito.ArgumentMatchers.eq("CCTV_001")))
                .willReturn(Mockito.mock(Cctv.class));
        given(congestionConfigQueryService.getConfigFor(any())).willReturn(
                CongestionConfigQueryResponse.active(
                        "session-uuid",
                        "CCTV_001",
                        2.0,
                        com.saferoute.domain.congestion.entity.CongestionConfig.createDefault()
                )
        );

        mockMvc.perform(get("/api/v1/device/congestion-config").param("cctvCode", "CCTV_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingActive").value(true))
                .andExpect(jsonPath("$.trainingSessionId").value("session-uuid"))
                .andExpect(jsonPath("$.monitoredAreaM2").value(2.0))
                .andExpect(jsonPath("$.congestionThresholds.CAUTION_FROM").value(2.0))
                .andExpect(jsonPath("$.eventDetection.cooldownSec").value(30));
    }

    @Test
    @DisplayName("훈련 중이 아니면 최소 정보만 반환한다")
    void getConfig_trainingInactive_returnsMinimalConfig() throws Exception {
        given(deviceAuthorizationService.validateCctv(any(), org.mockito.ArgumentMatchers.eq("CCTV_001")))
                .willReturn(Mockito.mock(Cctv.class));
        given(congestionConfigQueryService.getConfigFor(any())).willReturn(
                CongestionConfigQueryResponse.inactive(
                        "CCTV_001",
                        com.saferoute.domain.congestion.entity.CongestionConfig.createDefault()
                )
        );

        mockMvc.perform(get("/api/v1/device/congestion-config").param("cctvCode", "CCTV_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingActive").value(false))
                .andExpect(jsonPath("$.trainingSessionId").doesNotExist())
                .andExpect(jsonPath("$.monitoredAreaM2").doesNotExist())
                .andExpect(jsonPath("$.snapshotIntervalSec").doesNotExist())
                .andExpect(jsonPath("$.targetInferenceFps").doesNotExist())
                .andExpect(jsonPath("$.congestionThresholds").doesNotExist())
                .andExpect(jsonPath("$.eventDetection").doesNotExist())
                .andExpect(jsonPath("$.cctvCode").value("CCTV_001"))
                .andExpect(jsonPath("$.configVersion").value(1));
    }
}
