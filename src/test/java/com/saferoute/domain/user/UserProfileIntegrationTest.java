package com.saferoute.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("로그인한 사용자는 자신의 정보를 조회하고 수정할 수 있다")
    void getAndUpdateMyProfile() throws Exception {
        TestAccount account = signupAndLogin();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_SUCCESS_003"))
                .andExpect(jsonPath("$.result.email").value(account.email()))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.result.role").value("MANAGER"));

        String updatedEmail = "updated-" + account.email();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "updatedManager",
                                  "phoneNumber": "010-9999-8888",
                                  "email": "%s",
                                  "schoolName": "Updated School"
                                }
                                """.formatted(updatedEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_SUCCESS_004"))
                .andExpect(jsonPath("$.result.username").value("updatedManager"))
                .andExpect(jsonPath("$.result.email").value(updatedEmail));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value(updatedEmail));
    }

    @Test
    @DisplayName("다른 사용자가 사용 중인 이메일로 수정할 수 없다")
    void rejectDuplicateEmail() throws Exception {
        TestAccount first = signupAndLogin();
        TestAccount second = signupAndLogin();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + second.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(first.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER001"));
    }

    @Test
    @DisplayName("인증하지 않은 사용자는 내 정보를 조회할 수 없다")
    void rejectUnauthenticatedProfileRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private TestAccount signupAndLogin() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = unique + "@saferoute.com";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user%s",
                                  "password": "password123!",
                                  "email": "%s",
                                  "phoneNumber": "010-1234-5678",
                                  "schoolName": "SafeRoute School",
                                  "role": "MANAGER"
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

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = response.path("result").path("accessToken").asText();
        return new TestAccount(email, accessToken);
    }

    private record TestAccount(String email, String accessToken) {}
}
