package com.saferoute.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReissueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("유효한 refresh token으로 새 access token과 refresh token을 재발급받는다")
    void reissueWithValidRefreshToken() throws Exception {
        JsonNode loginResult = signupAndLoginNormalUser();
        String refreshToken = loginResult.path("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_SUCCESS_006"))
                .andExpect(jsonPath("$.result.accessToken").exists())
                .andExpect(jsonPath("$.result.refreshToken").exists())
                .andReturn();

        JsonNode reissueResponse = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("result");

        // refresh token은 매 발급마다 고유한 jti를 가지므로 재발급 시 항상 새 값으로 회전한다.
        assertThat(reissueResponse.path("refreshToken").asText())
                .isNotEqualTo(refreshToken);
    }

    @Test
    @DisplayName("사용한 refresh token은 재사용할 수 없다")
    void reusedRefreshTokenIsRejected() throws Exception {
        JsonNode loginResult = signupAndLoginNormalUser();
        String refreshToken = loginResult.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
    }

    @Test
    @DisplayName("access token으로는 재발급을 받을 수 없다")
    void rejectAccessTokenAsRefreshToken() throws Exception {
        JsonNode loginResult = signupAndLoginNormalUser();
        String accessToken = loginResult.path("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
    }

    @Test
    @DisplayName("형식이 올바르지 않은 refresh token은 거부한다")
    void rejectMalformedRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "not-a-valid-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
    }

    private JsonNode signupAndLoginNormalUser() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = unique + "@saferoute.com";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user%s",
                                  "password": "password123!",
                                  "email": "%s",
                                  "schoolName": "SafeRoute School",
                                  "role": "NORMAL"
                                }
                                """.formatted(unique, email)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("result");
    }
}
