package com.saferoute.domain.device.client;

import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

// Spring 서버가 라즈베리파이(Pi Flask 서버)에 유도등 방향 명령을 전달하는 책임을 전담한다.
// Pi 쪽 API 스펙: POST {piEndpoint}/lights/direction, body { lightCode, direction }
// 하드웨어(Pi) 구현은 아직 없어 실제 통신은 검증되지 않았다 - 스펙만 정의된 상태이며
// 로컬 Flask 스텁 서버로 대신 검증한다.
@Slf4j
@Component
public class IoTLightPiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String DIRECTION_PATH = "/lights/direction";

    private final WebClient webClient;

    public IoTLightPiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void sendDirection(String piEndpoint, String lightCode, IoTLightDirection direction) {
        try {
            webClient.post()
                    .uri(piEndpoint + DIRECTION_PATH)
                    .bodyValue(new PiLightDirectionRequest(lightCode, direction))
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
        } catch (RuntimeException exception) {
            log.warn("라즈베리파이 통신 실패: piEndpoint={}, lightCode={}", piEndpoint, lightCode, exception);
            throw new ApiException(IoTLightErrorCode.DEVICE_UNREACHABLE, exception);
        }
    }
}
