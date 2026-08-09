package com.saferoute.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String ALLOWED_ORIGIN =
            "http://localhost:3000";

    private static final String DENIED_ORIGIN =
            "http://localhost:9999";

    private static final String PROTECTED_ENDPOINT =
            "/api/v1/buildings";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("허용 Origin의 실제 요청에는 CORS 응답 헤더가 포함된다")
    void allowedOriginReceivesCorsResponseHeader() throws Exception {
        mockMvc.perform(
                        get(PROTECTED_ENDPOINT)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN
                ));
    }

    @Test
    @DisplayName("OPTIONS preflight 요청은 Security와 JWT 필터에서 차단되지 않는다")
    void preflightRequestIsAllowed() throws Exception {
        mockMvc.perform(
                        options(PROTECTED_ENDPOINT)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("GET")
                ));
    }

    @Test
    @DisplayName("Authorization과 Content-Type 요청 헤더를 사용하는 preflight를 허용한다")
    void authorizationAndContentTypeHeadersAreAllowed() throws Exception {
        mockMvc.perform(
                        options(PROTECTED_ENDPOINT)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Authorization,Content-Type"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Authorization")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Content-Type")
                ));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 preflight 요청은 거부한다")
    void deniedOriginDoesNotReceiveCorsPermission() throws Exception {
        mockMvc.perform(
                        options(PROTECTED_ENDPOINT)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        DENIED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    @DisplayName("CORS 적용 후에도 보호 API는 인증 없이 접근할 수 없다")
    void protectedEndpointStillRequiresAuthentication() throws Exception {
        mockMvc.perform(
                        get(PROTECTED_ENDPOINT)
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger와 기존 공개 인증 endpoint는 계속 공개된다")
    void swaggerAndPublicEndpointRemainPublic() throws Exception {
        mockMvc.perform(
                        get("/v3/api-docs")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN
                ));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }
}