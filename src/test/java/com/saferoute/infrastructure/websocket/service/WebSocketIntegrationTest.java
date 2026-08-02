package com.saferoute.infrastructure.websocket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.security.JwtTokenProvider;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// STOMP CONNECT 인증부터 SUBSCRIBE, 실제 이벤트 수신까지 확인하는 통합 테스트.
// JWT는 로그인 REST 호출 대신 JwtTokenProvider로 직접 발급한다(로그인 자체는
// SecurityAuthorizationIntegrationTest에서 이미 검증됨). WebSocket 인증 로직만 집중 검증한다.
//
// 주의: 이 테스트는 일부러 @Transactional을 붙이지 않는다. WebSocket 연결/구독 처리는
// 테스트 스레드와 다른 스레드(별도 DB 커넥션)에서 이루어지므로, 테스트 트랜잭션 안에서
// 커밋하지 않은 데이터는 인터셉터의 existsById 조회에서 보이지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private TrainingScenarioRepository trainingScenarioRepository;

    @Autowired
    private TrainingSessionRepository trainingSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TrainingEventPublisher trainingEventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private TrainingSession trainingSession;
    private TrainingScenario trainingScenario;
    private Building building;
    private User managerUser;
    private User normalUser;
    private String managerToken;
    private String normalToken;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);

        managerUser = User.create(
                "manager" + unique,
                passwordEncoder.encode("password123!"),
                unique + "-manager@saferoute.com",
                UserRole.MANAGER,
                "SafeRoute School"
        );
        normalUser = User.create(
                "normal" + unique,
                passwordEncoder.encode("password123!"),
                unique + "-normal@saferoute.com",
                UserRole.NORMAL,
                "SafeRoute School"
        );
        userRepository.save(managerUser);
        userRepository.save(normalUser);

        building = Building.create("공학관", "서울특별시 성북구 안전로 1", 5, BuildingType.CLASSROOM);
        buildingRepository.save(building);

        trainingScenario = TrainingScenario.create(
                "정기 훈련",
                50,
                Instant.now(),
                false,
                FireSpreadSpeed.MEDIUM,
                building,
                managerUser
        );
        trainingScenarioRepository.save(trainingScenario);

        trainingSession = TrainingSession.create(TrainingStatus.RUNNING, Instant.now(), managerUser, trainingScenario);
        trainingSessionRepository.save(trainingSession);

        managerToken = jwtTokenProvider.createAccessToken(managerUser);
        normalToken = jwtTokenProvider.createAccessToken(normalUser);
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();

        // @Transactional을 쓰지 않으므로 테스트가 만든 데이터를 직접 정리한다.
        trainingSessionRepository.delete(trainingSession);
        trainingScenarioRepository.delete(trainingScenario);
        buildingRepository.delete(building);
        userRepository.delete(managerUser);
        userRepository.delete(normalUser);
    }

    @Test
    @DisplayName("MANAGER 토큰으로 연결·구독하면 발행된 훈련 상태 이벤트를 수신한다")
    void managerCanConnectSubscribeAndReceiveEvent() throws Exception {
        StompSession session = connect(managerToken);

        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        session.subscribe(
                "/topic/training-sessions/" + trainingSession.getId(),
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        received.add(new String((byte[]) payload));
                    }
                }
        );

        // 구독이 브로커에 등록될 시간을 확보한다.
        Thread.sleep(300);

        trainingEventPublisher.publishTrainingStatusUpdated(trainingSession);

        String payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();

        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.get("eventType").asText()).isEqualTo("TRAINING_STATUS_UPDATED");
        assertThat(json.get("sessionId").asText()).isEqualTo(trainingSession.getId().toString());

        session.disconnect();
    }

    @Test
    @DisplayName("Authorization 헤더 없이 연결하면 실패한다")
    void connectWithoutTokenFails() {
        assertThatThrownBy(() -> connectRaw(null))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    @DisplayName("NORMAL 권한 토큰으로는 연결에 실패한다")
    void connectWithNormalRoleFails() {
        assertThatThrownBy(() -> connectRaw(normalToken))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    private StompSession connect(String token) throws Exception {
        return connectRaw(token);
    }

    private StompSession connectRaw(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        return stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        (org.springframework.web.socket.WebSocketHttpHeaders) null,
                        connectHeaders,
                        new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
                        }
                )
                .get(5, TimeUnit.SECONDS);
    }
}