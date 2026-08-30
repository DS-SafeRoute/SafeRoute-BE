package com.saferoute.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReissueConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("동일한 refresh token으로 동시에 재발급을 요청하면 한 요청만 성공한다")
    void concurrentReissueWithSameRefreshToken() throws Exception {
        String refreshToken = signupAndLoginNormalUser();
        String requestBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        List<MvcResult> results = reissueConcurrently(requestBody);

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 401);

        MvcResult rejected = results.stream()
                .filter(result -> result.getResponse().getStatus() == 401)
                .findFirst()
                .orElseThrow();
        JsonNode rejectedResponse = objectMapper.readTree(
                rejected.getResponse().getContentAsString()
        );
        assertThat(rejectedResponse.path("code").asText()).isEqualTo("USER005");
    }

    private List<MvcResult> reissueConcurrently(String requestBody) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<MvcResult> first = executor.submit(
                    () -> performReissue(requestBody, ready, start)
            );
            Future<MvcResult> second = executor.submit(
                    () -> performReissue(requestBody, ready, start)
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

    private MvcResult performReissue(
            String requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 신호를 기다리는 중 시간 초과되었습니다.");
        }

        return mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
    }

    private String signupAndLoginNormalUser() throws Exception {
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
                .path("refreshToken")
                .asText();
    }
}
