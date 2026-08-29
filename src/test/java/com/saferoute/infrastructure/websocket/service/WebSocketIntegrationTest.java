package com.saferoute.infrastructure.websocket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
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
    private FloorRepository floorRepository;

    @Autowired
    private MapNodeJpaRepository mapNodeJpaRepository;

    @Autowired
    private MapEdgeJpaRepository mapEdgeJpaRepository;

    @Autowired
    private IoTLightJpaRepository iotLightJpaRepository;

    @Autowired
    private com.saferoute.domain.training.service.TrainingSessionService trainingSessionService;

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
    // newScenario()가 만드는 Floor는 building FK를 갖고 있어, tearDown의 building 삭제보다
    // 먼저 정리해야 한다 (연결된 MapNode/MapEdge는 Floor 삭제에 cascade로 함께 지워진다).
    private final java.util.List<Floor> scenarioFloors = new java.util.ArrayList<>();

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

        building = Building.create("공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM, "SafeRoute School");
        buildingRepository.save(building);

        trainingScenario = TrainingScenario.create(
                "정기 훈련",
                50,
                Instant.now(),
                false,
                FireSpreadSpeed.MEDIUM,
                building,
                managerUser,
                null
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
        trainingSessionRepository.deleteById(trainingSession.getId());
        trainingScenarioRepository.delete(trainingScenario);
        scenarioFloors.forEach(floorRepository::delete);
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

    // 시나리오당 세션은 1개만 허용되므로(UNIQUE 제약), setUp()의 trainingScenario를 재사용하지 않고
    // 테스트마다 별도 시나리오를 만들어 추가 세션을 붙인다.
    // start() 통합 테스트가 최초 경로 계산을 실제로 성공시킬 수 있도록, 출발 노드에서
    // EXIT 노드까지 이어지는 최소 그래프(노드 2개 + 엣지 1개)를 함께 만든다.
    private TrainingScenario newScenario() {
        Floor floor = floorRepository.save(Floor.create(building, 1));
        scenarioFloors.add(floor);
        MapNode startNode = mapNodeJpaRepository.save(
                MapNode.create(floor, "START", NodeType.ROOM, "출발 지점", 0.1, 0.1, false));
        MapNode exitNode = mapNodeJpaRepository.save(
                MapNode.create(floor, "EXIT", NodeType.EXIT, "출구", 0.9, 0.9, true));
        mapEdgeJpaRepository.save(MapEdge.create(floor, startNode, exitNode, 3.0, true));

        TrainingScenario scenario = TrainingScenario.create(
                "정기 훈련", 50, Instant.now(), false, FireSpreadSpeed.MEDIUM, building, managerUser, startNode);
        return trainingScenarioRepository.save(scenario);
    }

    @Test
    @DisplayName("훈련을 시작하면 구독자가 TRAINING_STATUS_UPDATED(RUNNING) 이벤트를 수신한다")
    void startingTrainingPublishesRunningEvent() throws Exception {
        TrainingScenario scenario = newScenario();
        TrainingSession scheduledSession = TrainingSession.create(
                TrainingStatus.SCHEDULED, Instant.now(), managerUser, scenario);
        trainingSessionRepository.save(scheduledSession);

        try {
            StompSession session = connect(managerToken);
            BlockingQueue<String> received = subscribeAndCollect(session, scheduledSession.getId());

            trainingSessionService.start(scheduledSession.getId(), managerUser.getEmail());

            JsonNode json = objectMapper.readTree(received.poll(5, TimeUnit.SECONDS));
            assertThat(json.get("eventType").asText()).isEqualTo("TRAINING_STATUS_UPDATED");
            assertThat(json.get("data").get("status").asText()).isEqualTo("RUNNING");

            session.disconnect();
        } finally {
            trainingSessionRepository.deleteById(scheduledSession.getId());
            trainingScenarioRepository.delete(scenario);
        }
    }

    @Test
    @DisplayName("훈련을 정상 종료하면 구독자가 TRAINING_STATUS_UPDATED(COMPLETED) 이벤트를 수신한다")
    void endingTrainingPublishesCompletedEvent() throws Exception {
        TrainingScenario scenario = newScenario();
        TrainingSession runningSession = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now(), managerUser, scenario);
        trainingSessionRepository.save(runningSession);

        try {
            StompSession session = connect(managerToken);
            BlockingQueue<String> received = subscribeAndCollect(session, runningSession.getId());

            trainingSessionService.end(runningSession.getId(), managerUser.getEmail());

            JsonNode json = objectMapper.readTree(received.poll(5, TimeUnit.SECONDS));
            assertThat(json.get("data").get("status").asText()).isEqualTo("COMPLETED");

            session.disconnect();
        } finally {
            trainingSessionRepository.deleteById(runningSession.getId());
            trainingScenarioRepository.delete(scenario);
        }
    }

    @Test
    @DisplayName("훈련을 강제 종료하면 구독자가 TRAINING_STATUS_UPDATED(STOPPED) 이벤트를 수신한다")
    void forceEndingTrainingPublishesStoppedEvent() throws Exception {
        TrainingScenario scenario = newScenario();
        TrainingSession runningSession = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now(), managerUser, scenario);
        trainingSessionRepository.save(runningSession);

        try {
            StompSession session = connect(managerToken);
            BlockingQueue<String> received = subscribeAndCollect(session, runningSession.getId());

            trainingSessionService.forceEnd(runningSession.getId(), managerUser.getEmail());

            JsonNode json = objectMapper.readTree(received.poll(5, TimeUnit.SECONDS));
            assertThat(json.get("data").get("status").asText()).isEqualTo("STOPPED");

            session.disconnect();
        } finally {
            trainingSessionRepository.deleteById(runningSession.getId());
            trainingScenarioRepository.delete(scenario);
        }
    }

    @Test
    @DisplayName("MANAGER 토큰으로 층 유도등 topic을 구독하면 발행된 유도등 상태 이벤트를 수신한다")
    void managerCanSubscribeFloorLightsTopicAndReceiveEvent() throws Exception {
        Floor floor = Floor.create(building, 3);
        floorRepository.save(floor);
        MapNode customNode = mapNodeJpaRepository.save(MapNode.createCustom(floor, "LIGHT_TEST_001", "복도 유도등", 0.3, 0.4));
        IoTLight light = iotLightJpaRepository.save(IoTLight.create("LIGHT_TEST_001", "복도 유도등", customNode));

        try {
            StompSession session = connect(managerToken);

            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            session.subscribe(
                    "/topic/floors/" + floor.getId() + "/lights",
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

            trainingEventPublisher.publishIoTLightStatusUpdated(light, IoTLightDirection.LEFT);

            String payload = received.poll(5, TimeUnit.SECONDS);
            assertThat(payload).isNotNull();

            JsonNode json = objectMapper.readTree(payload);
            assertThat(json.get("eventType").asText()).isEqualTo("IOT_LIGHT_STATUS_UPDATED");
            assertThat(json.get("floorId").asText()).isEqualTo(floor.getId().toString());
            assertThat(json.get("data").get("lightId").asText()).isEqualTo(light.getId().toString());
            assertThat(json.get("data").get("direction").asText()).isEqualTo("LEFT");

            session.disconnect();
        } finally {
            iotLightJpaRepository.delete(light);
            mapNodeJpaRepository.delete(customNode);
            floorRepository.delete(floor);
        }
    }

    private BlockingQueue<String> subscribeAndCollect(StompSession session, java.util.UUID sessionId)
            throws InterruptedException {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        session.subscribe(
                "/topic/training-sessions/" + sessionId,
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
        return received;
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

    @Test
    @DisplayName("허용된 Origin에서는 WebSocket 연결에 성공한다")
    void allowedOriginCanConnect() throws Exception {
        StompSession session = connectRaw(managerToken, "http://localhost:3000");

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("허용되지 않은 Origin에서는 WebSocket handshake가 거부된다")
    void disallowedOriginCannotConnect() {
        assertThatThrownBy(() -> connectRaw(managerToken, "https://evil.example"))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    private StompSession connect(String token) throws Exception {
        return connectRaw(token);
    }

    private StompSession connectRaw(String token) throws Exception {
        return connectRaw(token, null);
    }

    private StompSession connectRaw(String token, String origin) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        if (origin != null) {
            handshakeHeaders.setOrigin(origin);
        }

        return stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        handshakeHeaders,
                        connectHeaders,
                        new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
                        }
                )
                .get(5, TimeUnit.SECONDS);
    }
}
