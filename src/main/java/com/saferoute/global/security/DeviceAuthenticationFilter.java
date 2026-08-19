package com.saferoute.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.global.api.code.BaseErrorCode;
import com.saferoute.global.api.error.DeviceErrorCode;
import com.saferoute.global.api.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEVICE_PATH_PREFIX = "/api/v1/device/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final DeviceTokenService deviceTokenService;
    private final CctvJpaRepository cctvJpaRepository;
    private final ObjectMapper objectMapper;

    public DeviceAuthenticationFilter(
            DeviceTokenService deviceTokenService,
            CctvJpaRepository cctvJpaRepository,
            ObjectMapper objectMapper
    ) {
        this.deviceTokenService = deviceTokenService;
        this.cctvJpaRepository = cctvJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith(DEVICE_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawToken = resolveToken(request);
        if (rawToken == null) {
            writeFailure(response, DeviceErrorCode.DEVICE_TOKEN_REQUIRED);
            return;
        }

        Cctv cctv = cctvJpaRepository.findByDeviceTokenHash(deviceTokenService.hash(rawToken))
                .orElse(null);
        if (cctv == null) {
            writeFailure(response, DeviceErrorCode.INVALID_DEVICE_TOKEN);
            return;
        }
        if (!cctv.isEnabled()) {
            writeFailure(response, DeviceErrorCode.CCTV_DISABLED);
            return;
        }

        DevicePrincipal principal = new DevicePrincipal(cctv.getId(), cctv.getCode());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeFailure(HttpServletResponse response, BaseErrorCode errorCode)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode));
    }
}
