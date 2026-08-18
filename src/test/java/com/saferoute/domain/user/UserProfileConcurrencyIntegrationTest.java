package com.saferoute.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest(name = "동시 {0} 수정")
    @EnumSource(UniqueProfileField.class)
    @DisplayName("두 사용자가 동일한 유니크 프로필 값으로 동시에 수정하면 한 요청만 성공한다")
    void concurrentUniqueProfileUpdate(UniqueProfileField field) throws Exception {
        String firstToken = signupAndLogin();
        String secondToken = signupAndLogin();
        String value = field.uniqueValue();
        String requestBody = field.requestBody(value);

        List<MvcResult> results = patchConcurrently(firstToken, secondToken, requestBody);

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 409);

        MvcResult conflict = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow();
        JsonNode conflictResponse = objectMapper.readTree(
                conflict.getResponse().getContentAsString()
        );
        assertThat(conflictResponse.path("code").asText()).isEqualTo(field.errorCode());
    }

    private List<MvcResult> patchConcurrently(
            String firstToken,
            String secondToken,
            String requestBody
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<MvcResult> first = executor.submit(
                    () -> performPatch(firstToken, requestBody, ready, start)
            );
            Future<MvcResult> second = executor.submit(
                    () -> performPatch(secondToken, requestBody, ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult performPatch(
            String accessToken,
            String requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 신호를 기다리는 중 시간 초과되었습니다.");
        }

        return mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
    }

    private String signupAndLogin() throws Exception {
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

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("result")
                .path("accessToken")
                .asText();
    }

    private enum UniqueProfileField {
        EMAIL("USER001") {
            @Override
            String uniqueValue() {
                return "shared-" + shortUuid() + "@saferoute.com";
            }

            @Override
            String requestBody(String value) {
                return "{\"email\":\"" + value + "\"}";
            }
        },
        USERNAME("USER002") {
            @Override
            String uniqueValue() {
                return "shared" + shortUuid();
            }

            @Override
            String requestBody(String value) {
                return "{\"username\":\"" + value + "\"}";
            }
        };

        private final String errorCode;

        UniqueProfileField(String errorCode) {
            this.errorCode = errorCode;
        }

        abstract String uniqueValue();

        abstract String requestBody(String value);

        String errorCode() {
            return errorCode;
        }

        static String shortUuid() {
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
