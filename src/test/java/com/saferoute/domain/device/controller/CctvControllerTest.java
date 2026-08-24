package com.saferoute.domain.device.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.device.dto.request.ConfigureCctvGridCellsRequest;
import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.request.UpdateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvGridCellResponse;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.dto.response.CctvRegistrationResponse;
import com.saferoute.domain.device.dto.response.DeviceTokenIssueResponse;
import com.saferoute.domain.device.service.CctvService;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CctvController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class CctvControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CctvService cctvService;

    private final UUID cctvId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();
    private final UUID cellId = UUID.randomUUID();

    @Test
    void createCctv_returnsCreatedCoverage() throws Exception {
        CreateCctvRequest request = new CreateCctvRequest(
                "3층 복도 CCTV", floorId, 0.6, 0.4, List.of(cellId));
        given(cctvService.createCctv(any())).willReturn(
                new CctvRegistrationResponse(response(true), "device-token")
        );

        mockMvc.perform(post("/api/v1/cctvs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.cctv.x").value(0.6))
                .andExpect(jsonPath("$.result.cctv.gridCells[0].id").value(cellId.toString()))
                .andExpect(jsonPath("$.result.cctv.monitoredAreaM2").value(0.25))
                .andExpect(jsonPath("$.result.deviceToken").value("device-token"));
    }

    @Test
    void createCctv_rejectsEmptyGridCells() throws Exception {
        CreateCctvRequest request = new CreateCctvRequest(
                "3층 복도 CCTV", floorId, 0.6, 0.4, List.of());

        mockMvc.perform(post("/api/v1/cctvs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issueDeviceToken_returnsRawTokenOnce() throws Exception {
        given(cctvService.issueDeviceToken(cctvId))
                .willReturn(new DeviceTokenIssueResponse("device-token"));

        mockMvc.perform(post("/api/v1/cctvs/{cctvId}/device-token", cctvId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.deviceToken").value("device-token"));
    }

    @Test
    void createCctv_rejectsOutOfRangeCoordinates() throws Exception {
        CreateCctvRequest request = new CreateCctvRequest(
                "3층 복도 CCTV", floorId, 1.1, -0.1, List.of(cellId));

        mockMvc.perform(post("/api/v1/cctvs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCctvs_returnsList() throws Exception {
        given(cctvService.getCctvs(floorId)).willReturn(List.of(response(true)));

        mockMvc.perform(get("/api/v1/cctvs").param("floorId", floorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
    }

    @Test
    void configureGridCells_returnsUpdatedCoverage() throws Exception {
        ConfigureCctvGridCellsRequest request =
                new ConfigureCctvGridCellsRequest(List.of(cellId));
        given(cctvService.configureGridCells(eq(cctvId), any())).willReturn(response(true));

        mockMvc.perform(put("/api/v1/cctvs/{cctvId}/grid-cells", cctvId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.monitoredGridCellCount").value(1));
    }

    @Test
    void updateCctv_returnsUpdatedNameAndPosition() throws Exception {
        UpdateCctvRequest request = new UpdateCctvRequest("1층 출입구 CCTV", 0.4, 0.6);
        given(cctvService.updateCctv(eq(cctvId), any()))
                .willReturn(response(true, request.name(), request.x(), request.y()));

        mockMvc.perform(patch("/api/v1/cctvs/{cctvId}", cctvId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CCTV_SUCCESS_008"))
                .andExpect(jsonPath("$.result.name").value(request.name()))
                .andExpect(jsonPath("$.result.x").value(request.x()))
                .andExpect(jsonPath("$.result.y").value(request.y()));
    }

    @Test
    void updateCctv_rejectsBlankName() throws Exception {
        UpdateCctvRequest request = new UpdateCctvRequest(" ", 0.4, 0.6);

        performInvalidUpdate(request);
    }

    @Test
    void updateCctv_rejectsNameLongerThan100Characters() throws Exception {
        UpdateCctvRequest request = new UpdateCctvRequest("a".repeat(101), 0.4, 0.6);

        performInvalidUpdate(request);
    }

    @Test
    void updateCctv_rejectsXBelowMinimum() throws Exception {
        UpdateCctvRequest request = new UpdateCctvRequest("1층 출입구 CCTV", -0.1, 0.6);

        performInvalidUpdate(request);
    }

    @Test
    void updateCctv_rejectsYAboveMaximum() throws Exception {
        UpdateCctvRequest request = new UpdateCctvRequest("1층 출입구 CCTV", 0.4, 1.1);

        performInvalidUpdate(request);
    }

    private void performInvalidUpdate(UpdateCctvRequest request) throws Exception {
        mockMvc.perform(patch("/api/v1/cctvs/{cctvId}", cctvId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableCctv_returnsDisabledState() throws Exception {
        given(cctvService.disableCctv(cctvId)).willReturn(response(false));

        mockMvc.perform(patch("/api/v1/cctvs/{cctvId}/disable", cctvId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.enabled").value(false));
    }

    private CctvResponse response(boolean enabled) {
        return response(enabled, "3층 복도 CCTV", 0.6, 0.4);
    }

    private CctvResponse response(boolean enabled, String name, double x, double y) {
        return new CctvResponse(
                cctvId,
                "CCTV_001",
                name,
                floorId,
                UUID.randomUUID(),
                x,
                y,
                enabled,
                0.5,
                1,
                0.25,
                List.of(new CctvGridCellResponse(cellId, 1, 2, true, 0.3, 0.4))
        );
    }
}
