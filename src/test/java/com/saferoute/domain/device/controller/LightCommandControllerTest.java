package com.saferoute.domain.device.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.device.dto.request.AckLightCommandRequest;
import com.saferoute.domain.device.dto.response.LightCommandAckResponse;
import com.saferoute.domain.device.dto.response.LightCommandListResponse;
import com.saferoute.domain.device.dto.response.LightCommandResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.entity.LightCommandStatus;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.device.service.LightCommandService;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.DeviceAuthenticationFilter;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.List;
import java.util.UUID;
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
        controllers = LightCommandController.class,
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
class LightCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeviceAuthorizationService deviceAuthorizationService;

    @MockitoBean
    private LightCommandService lightCommandService;

    // === pollCommands ===

    @Test
    @DisplayName("GET /device/light-commands - cctvCode 없이 요청하면 400을 반환한다")
    void pollCommands_missingCctvCode_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/device/light-commands"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /device/light-commands - 담당 유도등의 PENDING 명령 목록을 반환한다")
    void pollCommands_success() throws Exception {
        given(deviceAuthorizationService.validateCctv(any(), eq("CCTV_001")))
                .willReturn(Mockito.mock(Cctv.class));
        given(lightCommandService.pollCommands(any())).willReturn(
                new LightCommandListResponse(List.of(
                        new LightCommandResponse(UUID.randomUUID(), "LIGHT_001", IoTLightDirection.LEFT)
                ))
        );

        mockMvc.perform(get("/api/v1/device/light-commands").param("cctvCode", "CCTV_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands[0].lightCode").value("LIGHT_001"))
                .andExpect(jsonPath("$.commands[0].direction").value("LEFT"));
    }

    // === ack ===

    @Test
    @DisplayName("PATCH /device/light-commands/{commandId}/ack - success 없이 요청하면 400을 반환한다")
    void ack_missingSuccess_returnsBadRequest() throws Exception {
        String invalidJson = """
                {}
                """;

        mockMvc.perform(patch("/api/v1/device/light-commands/{commandId}/ack", UUID.randomUUID())
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /device/light-commands/{commandId}/ack - 성공 보고 시 200과 ACKED 상태를 반환한다")
    void ack_success() throws Exception {
        UUID commandId = UUID.randomUUID();
        AckLightCommandRequest request = new AckLightCommandRequest(true, null);
        given(lightCommandService.ack(eq(commandId), any(), any()))
                .willReturn(new LightCommandAckResponse(commandId, LightCommandStatus.ACKED));

        mockMvc.perform(patch("/api/v1/device/light-commands/{commandId}/ack", commandId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKED"));
    }

    @Test
    @DisplayName("PATCH /device/light-commands/{commandId}/ack - 존재하지 않는 명령이면 404를 반환한다")
    void ack_commandNotFound_returnsNotFound() throws Exception {
        UUID commandId = UUID.randomUUID();
        AckLightCommandRequest request = new AckLightCommandRequest(true, null);
        given(lightCommandService.ack(eq(commandId), any(), any()))
                .willThrow(new ApiException(IoTLightErrorCode.LIGHT_COMMAND_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/device/light-commands/{commandId}/ack", commandId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
