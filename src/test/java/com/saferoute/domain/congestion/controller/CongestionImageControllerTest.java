package com.saferoute.domain.congestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saferoute.domain.congestion.dto.response.PresignedImageUrlResponse;
import com.saferoute.domain.congestion.service.CongestionImageService;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.DeviceAuthenticationFilter;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.UUID;
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
        controllers = CongestionImageController.class,
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
class CongestionImageControllerTest {

    private static final String API =
            "/api/v1/device/congestion-images/presigned-url";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private CongestionImageService congestionImageService;

    @Test
    void returnsPresignedUploadUrl() throws Exception {
        given(congestionImageService.createUploadUrl(any(), any()))
                .willReturn(new PresignedImageUrlResponse(
                        "training/session/monitoring/CCTV_001/1000.jpg",
                        "https://example.com/upload",
                        1060L
                ));

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey")
                        .value("training/session/monitoring/CCTV_001/1000.jpg"))
                .andExpect(jsonPath("$.uploadUrl").value("https://example.com/upload"))
                .andExpect(jsonPath("$.expiresAt").value(1060L));
    }

    @Test
    void rejectsContentTypeOtherThanJpeg() throws Exception {
        ObjectNode request = validRequest();
        request.put("contentType", "image/png");

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.contentType")
                        .value("contentType must be image/jpeg"));
    }

    @Test
    void rejectsUnknownImageType() throws Exception {
        ObjectNode request = validRequest();
        request.put("imageType", "UNKNOWN");

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCctvCodeThatCanAlterObjectPath() throws Exception {
        ObjectNode request = validRequest();
        request.put("cctvCode", "../CCTV_002");

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private ObjectNode validRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("trainingSessionId", UUID.randomUUID().toString());
        request.put("cctvCode", "CCTV_001");
        request.put("imageType", "MONITORING");
        request.put("referenceId", UUID.randomUUID().toString());
        request.put("capturedAt", 1786500005000L);
        request.put("contentType", "image/jpeg");
        return request;
    }
}
