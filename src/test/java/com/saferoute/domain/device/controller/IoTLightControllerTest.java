package com.saferoute.domain.device.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.device.dto.request.ChangeLightDirectionRequest;
import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdatePiEndpointRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.dto.response.LightDirectionResponse;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
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

// JwtAuthenticationFilter는 Filter 타입이라 @WebMvcTest 슬라이스에 기본으로 딸려 들어오는데,
// 그 생성자가 필요로 하는 JwtTokenProvider/CustomUserDetailsService/JwtAuthenticationEntryPoint는
// 평범한 @Component/@Service라 슬라이스에 안 올라와서 NoSuchBeanDefinitionException이 난다.
// addFilters=false로 어차피 필터를 실제로 적용하지 않으므로, 필터/보안 설정 자체를 슬라이스에서 뺀다.
@WebMvcTest(
        controllers = IoTLightController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class IoTLightControllerTest {

    private static final String EMAIL = "manager@saferoute.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IoTLightService iotLightService;

    private final UUID lightId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    private IoTLightResponse sampleResponse() {
        return new IoTLightResponse(lightId, "LIGHT_001", "복도1 유도등", floorId, 0.3, 0.4,
                null, null, null, false, true, null);
    }

    // === createLight ===

    @Test
    @DisplayName("POST /lights - 유도등을 등록하면 201을 반환한다")
    void createLight_success() throws Exception {
        CreateIoTLightRequest request = new CreateIoTLightRequest(floorId, "복도1 유도등", 0.3, 0.4);
        given(iotLightService.createLight(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/lights")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.code").value("LIGHT_001"));
    }

    @Test
    @DisplayName("POST /lights - 층이 없으면 404를 반환한다")
    void createLight_floorNotFound() throws Exception {
        CreateIoTLightRequest request = new CreateIoTLightRequest(floorId, "복도1 유도등", 0.3, 0.4);
        given(iotLightService.createLight(any()))
                .willThrow(new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        mockMvc.perform(post("/api/v1/lights")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("도면을 찾을 수 없습니다")));
    }

    @Test
    @DisplayName("POST /lights - name이 비어있으면 400을 반환한다")
    void createLight_blankName_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"floorId":"%s","name":"","x":0.3,"y":0.4}
                """.formatted(floorId);

        mockMvc.perform(post("/api/v1/lights")
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // === getLights / getLight ===

    @Test
    @DisplayName("GET /lights - 층별로 유도등 목록을 조회한다")
    void getLights_success() throws Exception {
        given(iotLightService.getLights(floorId, EMAIL)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/lights")
                        .param("floorId", floorId.toString())
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
    }

    @Test
    @DisplayName("GET /lights/{lightId} - 유도등이 없으면 404를 반환한다")
    void getLight_notFound() throws Exception {
        given(iotLightService.getLight(lightId, EMAIL))
                .willThrow(new ApiException(IoTLightErrorCode.IOT_LIGHT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/lights/{lightId}", lightId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isNotFound());
    }

    // === configureGuidance ===

    @Test
    @DisplayName("PATCH /lights/{lightId}/guidance - 분기 경로를 설정하면 200을 반환한다")
    void configureGuidance_success() throws Exception {
        ConfigureGuidanceRequest request =
                new ConfigureGuidanceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(iotLightService.configureGuidance(eq(lightId), any())).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/lights/{lightId}/guidance", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/guidance - 연결 안 된 엣지면 400을 반환한다")
    void configureGuidance_invalidEdge_returnsBadRequest() throws Exception {
        ConfigureGuidanceRequest request =
                new ConfigureGuidanceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(iotLightService.configureGuidance(eq(lightId), any()))
                .willThrow(new ApiException(IoTLightErrorCode.INVALID_GUIDANCE_EDGE));

        mockMvc.perform(patch("/api/v1/lights/{lightId}/guidance", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // === updateLight ===

    @Test
    @DisplayName("PATCH /lights/{lightId} - 이름/위치를 수정하면 200을 반환한다")
    void updateLight_success() throws Exception {
        UpdateIoTLightRequest request = new UpdateIoTLightRequest("변경된 이름", 0.7, 0.8);
        given(iotLightService.updateLight(eq(lightId), any())).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/lights/{lightId}", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // === enable / disable ===

    @Test
    @DisplayName("PATCH /lights/{lightId}/enable - 활성화하면 200을 반환한다")
    void enableLight_success() throws Exception {
        given(iotLightService.enableLight(lightId)).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/lights/{lightId}/enable", lightId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/disable - 비활성화하면 200을 반환한다")
    void disableLight_success() throws Exception {
        given(iotLightService.disableLight(lightId)).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/lights/{lightId}/disable", lightId))
                .andExpect(status().isOk());
    }

    // === updatePiEndpoint ===

    @Test
    @DisplayName("PATCH /lights/{lightId}/pi-endpoint - 라즈베리파이 주소를 설정하면 200을 반환한다")
    void updatePiEndpoint_success() throws Exception {
        UpdatePiEndpointRequest request = new UpdatePiEndpointRequest("http://192.168.0.50:5000");
        given(iotLightService.updatePiEndpoint(eq(lightId), any())).willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/lights/{lightId}/pi-endpoint", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/pi-endpoint - 값이 비어있으면 400을 반환한다")
    void updatePiEndpoint_blank_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"piEndpoint":""}
                """;

        mockMvc.perform(patch("/api/v1/lights/{lightId}/pi-endpoint", lightId)
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // === changeDirection ===

    @Test
    @DisplayName("PATCH /lights/{lightId}/direction - 방향 명령을 보내면 200을 반환한다")
    void changeDirection_success() throws Exception {
        ChangeLightDirectionRequest request = new ChangeLightDirectionRequest(IoTLightDirection.LEFT);
        given(iotLightService.changeDirection(eq(lightId), any()))
                .willReturn(new LightDirectionResponse(lightId, IoTLightDirection.LEFT, java.time.Instant.now()));

        mockMvc.perform(patch("/api/v1/lights/{lightId}/direction", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.direction").value("LEFT"));
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/direction - 경로 안내가 없으면 400을 반환한다")
    void changeDirection_guidanceNotConfigured_returnsBadRequest() throws Exception {
        ChangeLightDirectionRequest request = new ChangeLightDirectionRequest(IoTLightDirection.LEFT);
        given(iotLightService.changeDirection(eq(lightId), any()))
                .willThrow(new ApiException(IoTLightErrorCode.GUIDANCE_NOT_CONFIGURED));

        mockMvc.perform(patch("/api/v1/lights/{lightId}/direction", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/direction - 비활성화된 유도등이면 400을 반환한다")
    void changeDirection_lightDisabled_returnsBadRequest() throws Exception {
        ChangeLightDirectionRequest request = new ChangeLightDirectionRequest(IoTLightDirection.OFF);
        given(iotLightService.changeDirection(eq(lightId), any()))
                .willThrow(new ApiException(IoTLightErrorCode.LIGHT_DISABLED));

        mockMvc.perform(patch("/api/v1/lights/{lightId}/direction", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/direction - Pi와 통신 실패 시 502를 반환한다")
    void changeDirection_deviceUnreachable_returnsBadGateway() throws Exception {
        ChangeLightDirectionRequest request = new ChangeLightDirectionRequest(IoTLightDirection.OFF);
        given(iotLightService.changeDirection(eq(lightId), any()))
                .willThrow(new ApiException(IoTLightErrorCode.DEVICE_UNREACHABLE));

        mockMvc.perform(patch("/api/v1/lights/{lightId}/direction", lightId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("PATCH /lights/{lightId}/direction - direction이 없으면 400을 반환한다")
    void changeDirection_missingDirection_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/lights/{lightId}/direction", lightId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // === deleteLight ===

    @Test
    @DisplayName("DELETE /lights/{lightId} - 유도등을 삭제하면 200을 반환한다")
    void deleteLight_success() throws Exception {
        mockMvc.perform(delete("/api/v1/lights/{lightId}", lightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("DELETE /lights/{lightId} - 유도등이 없으면 404를 반환한다")
    void deleteLight_notFound() throws Exception {
        org.mockito.BDDMockito.willThrow(new ApiException(IoTLightErrorCode.IOT_LIGHT_NOT_FOUND))
                .given(iotLightService).deleteLight(lightId);

        mockMvc.perform(delete("/api/v1/lights/{lightId}", lightId))
                .andExpect(status().isNotFound());
    }
}
