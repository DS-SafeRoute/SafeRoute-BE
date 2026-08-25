package com.saferoute.domain.congestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.congestion.dto.response.CongestionImageUrlResponse;
import com.saferoute.domain.congestion.service.CongestionImageUrlService;
import com.saferoute.global.api.error.CongestionErrorCode;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CongestionImageUrlController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class CongestionImageUrlControllerTest {

    private static final String EMAIL = "manager@saferoute.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CongestionImageUrlService congestionImageUrlService;

    private final UUID eventId = UUID.randomUUID();

    @Test
    @DisplayName("이벤트 이미지 URL 조회 성공 시 200을 반환한다")
    void getEventImageUrl_returnsOk() throws Exception {
        given(congestionImageUrlService.getEventImageUrl(eventId, EMAIL))
                .willReturn(new CongestionImageUrlResponse("https://example.com/view", Instant.now()));

        mockMvc.perform(get("/api/v1/congestion-events/{eventId}/image-url", eventId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.imageUrl").value("https://example.com/view"));
    }

    @Test
    @DisplayName("이벤트를 찾을 수 없으면 404를 반환한다")
    void getEventImageUrl_returnsNotFoundWhenMissing() throws Exception {
        willThrow(new ApiException(CongestionErrorCode.EVENT_NOT_FOUND))
                .given(congestionImageUrlService).getEventImageUrl(any(), eq(EMAIL));

        mockMvc.perform(get("/api/v1/congestion-events/{eventId}/image-url", eventId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이벤트 이미지가 아직 없으면 409를 반환한다")
    void getEventImageUrl_returnsConflictWhenImageNotReady() throws Exception {
        willThrow(new ApiException(CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND))
                .given(congestionImageUrlService).getEventImageUrl(any(), eq(EMAIL));

        mockMvc.perform(get("/api/v1/congestion-events/{eventId}/image-url", eventId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("관측값 이미지 URL 조회 성공 시 200을 반환한다")
    void getObservationImageUrl_returnsOk() throws Exception {
        given(congestionImageUrlService.getObservationImageUrl(eventId, EMAIL))
                .willReturn(new CongestionImageUrlResponse("https://example.com/view", Instant.now()));

        mockMvc.perform(get("/api/v1/congestion-observations/{eventId}/image-url", eventId)
                        .principal(new UsernamePasswordAuthenticationToken(EMAIL, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.imageUrl").value("https://example.com/view"));
    }
}
