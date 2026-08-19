package com.saferoute.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class DeviceAuthenticationFilterTest {

    private DeviceTokenService deviceTokenService;
    private CctvJpaRepository cctvJpaRepository;
    private DeviceAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        deviceTokenService = mock(DeviceTokenService.class);
        cctvJpaRepository = mock(CctvJpaRepository.class);
        filter = new DeviceAuthenticationFilter(
                deviceTokenService,
                cctvJpaRepository,
                new ObjectMapper()
        );
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        MockHttpServletResponse response = execute(null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("DEVICE001");
        verify(filterChain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsUnknownToken() throws Exception {
        given(deviceTokenService.hash("unknown")).willReturn("hash");
        given(cctvJpaRepository.findByDeviceTokenHash("hash")).willReturn(Optional.empty());

        MockHttpServletResponse response = execute("Bearer unknown");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("DEVICE002");
    }

    @Test
    void rejectsDisabledCctv() throws Exception {
        Cctv cctv = cctv(false);
        given(deviceTokenService.hash("token")).willReturn("hash");
        given(cctvJpaRepository.findByDeviceTokenHash("hash")).willReturn(Optional.of(cctv));

        MockHttpServletResponse response = execute("Bearer token");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("DEVICE004");
    }

    @Test
    void authenticatesEnabledCctv() throws Exception {
        UUID cctvId = UUID.randomUUID();
        Cctv cctv = cctv(true);
        given(cctv.getId()).willReturn(cctvId);
        given(cctv.getCode()).willReturn("CCTV_001");
        given(deviceTokenService.hash("token")).willReturn("hash");
        given(cctvJpaRepository.findByDeviceTokenHash("hash")).willReturn(Optional.of(cctv));

        MockHttpServletResponse response = execute("Bearer token");

        assertThat(response.getStatus()).isEqualTo(200);
        DevicePrincipal principal = (DevicePrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal.cctvId()).isEqualTo(cctvId);
        assertThat(principal.cctvCode()).isEqualTo("CCTV_001");
        verify(filterChain).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private MockHttpServletResponse execute(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/device/congestion-events"
        );
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        return response;
    }

    private Cctv cctv(boolean enabled) {
        Cctv cctv = mock(Cctv.class);
        given(cctv.isEnabled()).willReturn(enabled);
        return cctv;
    }
}
