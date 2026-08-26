package com.saferoute.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.user.entity.UserRole;
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
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("인증 없이 보호 API를 호출하면 401을 반환한다")
    void unauthenticatedRequestReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("COMMON401")
                );
    }

    @Test
    @DisplayName("일반 사용자는 조회할 수 있지만 등록할 수 없다")
    void normalUserCanReadButCannotWrite()
            throws Exception {

        String token = signupAndLogin(UserRole.NORMAL);

        mockMvc.perform(
                        get("/api/v1/buildings")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/buildings")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(buildingRequest())
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("COMMON403")
                );
    }

    @Test
    @DisplayName("일반 사용자는 관리자용 GridCell 원본 목록을 조회할 수 없다")
    void normalUserCannotReadFloorGridCells() throws Exception {
        String token = signupAndLogin(UserRole.NORMAL);

        mockMvc.perform(
                        get("/api/v1/floors/{floorId}/grid/cells", UUID.randomUUID())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));
    }

    @Test
    @DisplayName("관리자는 GridCell 원본 목록 조회 엔드포인트에 접근할 수 있다")
    void managerCanReadFloorGridCells() throws Exception {
        String token = signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(
                        get("/api/v1/floors/{floorId}/grid/cells", UUID.randomUUID())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("일반 사용자는 경로 이탈률 조회 엔드포인트에 접근할 수 없다")
    void normalUserCannotReadRouteDeviation() throws Exception {
        String token = signupAndLogin(UserRole.NORMAL);

        mockMvc.perform(
                        get("/api/v1/lights/{lightId}/deviation", UUID.randomUUID())
                                .param("trainingSessionId", UUID.randomUUID().toString())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));
    }

    @Test
    @DisplayName("관리자는 경로 이탈률 조회 엔드포인트에 접근할 수 있다")
    void managerCanReadRouteDeviation() throws Exception {
        String token = signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(
                        get("/api/v1/lights/{lightId}/deviation", UUID.randomUUID())
                                .param("trainingSessionId", UUID.randomUUID().toString())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("일반 사용자는 훈련 모니터링 카메라 목록을 조회할 수 없다")
    void normalUserCannotReadTrainingMonitoringCameras() throws Exception {
        String token = signupAndLogin(UserRole.NORMAL);

        mockMvc.perform(
                        get("/api/v1/sessions/{sessionId}/monitoring/cameras", UUID.randomUUID())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));
    }

    @Test
    @DisplayName("관리자는 훈련 모니터링 카메라 목록 엔드포인트에 접근할 수 있다")
    void managerCanReadTrainingMonitoringCameras() throws Exception {
        String token = signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(
                        get("/api/v1/sessions/{sessionId}/monitoring/cameras", UUID.randomUUID())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAINING001"));
    }

    @Test
    @DisplayName("Swagger에 훈련 모니터링 카메라 API와 응답 예시가 노출된다")
    void trainingMonitoringApiIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/sessions/{sessionId}/monitoring/cameras'].get.summary"
                ).value("카메라별 최신 캡처 목록 조회"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/sessions/{sessionId}/monitoring/cameras']"
                                + ".get.responses['200'].content['application/json'].examples"
                                + ".['최신 캡처가 있는 카메라'].value.result.cameras[0].capturedAt"
                ).value(1_787_722_095_000L))
                .andExpect(jsonPath(
                        "$.components.schemas.MonitoringCameraResponse.properties.thumbnailUrl.description"
                ).value(org.hamcrest.Matchers.containsString("presigned GET URL")))
                .andExpect(jsonPath(
                        "$.components.schemas.MonitoringCameraResponse.properties.capturedAt.description"
                ).value(org.hamcrest.Matchers.containsString("epoch milliseconds")));
    }

    @Test
    @DisplayName("관리자는 건물을 등록할 수 있다")
    void managerCanWrite() throws Exception {
        String token =
                signupAndLogin(UserRole.MANAGER);

        mockMvc.perform(
                        post("/api/v1/buildings")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(buildingRequest())
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.isSuccess")
                                .value(true)
                );
    }

    @Test
    @DisplayName("변조된 JWT를 전송하면 401을 반환한다")
    void tamperedTokenReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/buildings")
                                .header(
                                        "Authorization",
                                        "Bearer invalid.jwt.token"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("COMMON401")
                );
    }

    private String signupAndLogin(UserRole role)
            throws Exception {

        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        String email =
                unique + "@saferoute.com";

        String signupRequest = """
                {
                  "username": "%s",
                  "password": "password123!",
                  "email": "%s",
                  "schoolName": "SafeRoute School",
                  "role": "%s"
                }
                """.formatted(
                "user" + unique,
                email,
                role.name()
        );

        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(signupRequest)
                )
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "password123!"
                                        }
                                        """.formatted(email))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.result.tokenType")
                                .value("Bearer")
                )
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        return response
                .path("result")
                .path("accessToken")
                .asText();
    }

    private String buildingRequest() {
        return """
                {
                  "name": "공학관",
                  "address": "서울특별시 성북구 안전로 1",
                  "buildingType": "CLASSROOM"
                }
                """;
    }
}
