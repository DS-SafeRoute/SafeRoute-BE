package com.saferoute.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.user.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
        TestAccount account = signupAndLogin(UserRole.MANAGER);

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
                .andExpect(jsonPath("$.result.email").value(updatedEmail))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-9999-8888"))
                .andExpect(jsonPath("$.result.schoolName").value("Updated School"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("updatedManager"))
                .andExpect(jsonPath("$.result.email").value(updatedEmail))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-9999-8888"))
                .andExpect(jsonPath("$.result.schoolName").value("Updated School"));

        String partiallyUpdatedEmail = "partial-" + account.email();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(partiallyUpdatedEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("updatedManager"))
                .andExpect(jsonPath("$.result.email").value(partiallyUpdatedEmail))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-9999-8888"))
                .andExpect(jsonPath("$.result.schoolName").value("Updated School"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("updatedManager"))
                .andExpect(jsonPath("$.result.email").value(partiallyUpdatedEmail))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-9999-8888"))
                .andExpect(jsonPath("$.result.schoolName").value("Updated School"));
    }

    @Test
    @DisplayName("다른 사용자가 사용 중인 이메일로 수정할 수 없다")
    void rejectDuplicateEmail() throws Exception {
        TestAccount first = signupAndLogin(UserRole.MANAGER);
        TestAccount second = signupAndLogin(UserRole.MANAGER);

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
    @DisplayName("다른 사용자가 사용 중인 사용자명으로 수정할 수 없다")
    void rejectDuplicateUsername() throws Exception {
        TestAccount first = signupAndLogin(UserRole.MANAGER);
        TestAccount second = signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + second.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s"}
                                """.formatted(first.username())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER002"));
    }

    @Test
    @DisplayName("유효하지 않은 이메일은 수정할 수 없다")
    void rejectInvalidEmail() throws Exception {
        TestAccount account = signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invalid-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    @DisplayName("유효하지 않은 전화번호는 수정할 수 없다")
    void rejectInvalidPhoneNumber() throws Exception {
        TestAccount account = signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber": "010-ABCD-5678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @ParameterizedTest(name = "{0}은 프로필 조회·수정·로그아웃을 사용할 수 있다")
    @EnumSource(UserRole.class)
    @DisplayName("MANAGER와 NORMAL 모두 프로필 조회·수정·로그아웃을 사용할 수 있다")
    void profileAndLogoutRoleMatrix(UserRole role) throws Exception {
        TestAccount account = signupAndLogin(role);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value(role.name()));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber": "010-7777-6666"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phoneNumber").value("010-7777-6666"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_SUCCESS_005"));
    }

    @Test
    @DisplayName("인증하지 않은 사용자는 내 정보를 조회할 수 없다")
    void rejectUnauthenticatedProfileRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private TestAccount signupAndLogin(UserRole role) throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = unique + "@saferoute.com";
        String username = "user" + unique;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123!",
                                  "email": "%s",
                                  "phoneNumber": "010-1234-5678",
                                  "schoolName": "SafeRoute School",
                                  "role": "%s"
                                }
                                """.formatted(username, email, role.name())))
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
        return new TestAccount(username, email, accessToken);
    }

    private record TestAccount(String username, String email, String accessToken) {}
}
