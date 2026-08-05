package com.saferoute.infrastructure.websocket.service;

import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.infrastructure.websocket.dto.IoTLightEventMessage;
import com.saferoute.infrastructure.websocket.dto.IoTLightStatusEventData;
import com.saferoute.infrastructure.websocket.dto.TrainingEventMessage;
import com.saferoute.infrastructure.websocket.dto.TrainingEventType;
import com.saferoute.infrastructure.websocket.dto.TrainingStatusEventData;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 관리자 대시보드로 보내는 WebSocket 이벤트 발행 책임을 이 클래스가 전담한다.
// SimpMessagingTemplate을 다른 서비스에 직접 주입하지 않고 항상 이 클래스를 거친다.
// 훈련 세션 이벤트는 /topic/training-sessions/{sessionId}, 유도등 이벤트는 층 도면 화면이
// 층 전체를 한 번에 보여주는 구조라 /topic/floors/{floorId}/lights로 발행한다.
//
// 혼잡도 / 경로 재계산 이벤트 발행 메서드는 아직 없다.
// 대응하는 도메인 로직이 만들어지면 이 클래스에 전용 메서드를 추가한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingEventPublisher {

    private static final String SESSION_TOPIC_PREFIX = "/topic/training-sessions/";
    private static final String FLOOR_LIGHTS_TOPIC_PREFIX = "/topic/floors/";
    private static final String FLOOR_LIGHTS_TOPIC_SUFFIX = "/lights";

    private final SimpMessagingTemplate messagingTemplate;

    // 트랜잭션 커밋 시점과 무관하게 즉시 발행한다.
    // 커밋 이후에만 발행해야 하는 일반적인 경우에는 publishTrainingStatusUpdatedAfterCommit을 사용한다.
    public void publishTrainingStatusUpdated(TrainingSession session) {
        TrainingEventMessage<TrainingStatusEventData> message = TrainingEventMessage.of(
                TrainingEventType.TRAINING_STATUS_UPDATED,
                session.getId(),
                TrainingStatusEventData.from(session)
        );

        messagingTemplate.convertAndSend(SESSION_TOPIC_PREFIX + session.getId(), message);

        log.debug(
                "훈련 상태 이벤트 발행: sessionId={}, status={}",
                session.getId(),
                session.getStatus()
        );
    }

    // DB 트랜잭션이 실제로 커밋된 이후에만 이벤트를 발행한다.
    // 트랜잭션이 롤백되면 발행하지 않는다.
    // afterCommit() 콜백은 커밋이 끝난 뒤 같은 스레드에서 즉시 실행되므로,
    // 여기서 예외가 그대로 튀어나가면 DB 반영은 이미 끝났는데도 호출자에게는
    // 마치 요청 전체가 실패한 것처럼 보일 수 있다. 그래서 발행 실패는 여기서
    // 잡아서 로그만 남기고 삼킨다 — WebSocket 발행 실패가 REST 응답/트랜잭션
    // 성공 여부에 영향을 주면 안 되기 때문이다.
    public void publishTrainingStatusUpdatedAfterCommit(TrainingSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishTrainingStatusUpdated(session);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishTrainingStatusUpdated(session);
                        } catch (RuntimeException exception) {
                            log.error(
                                    "커밋 후 훈련 상태 이벤트 발행 실패: sessionId={}",
                                    session.getId(),
                                    exception
                            );
                        }
                    }
                }
        );
    }

    // floorId는 light.getCustomNode().getFloor().getId()로 조회한다.
    // direction은 IoTLight 엔티티가 아니라 IoTLightDirectionStore(서버 메모리)가 원천이므로 별도 파라미터로 받는다.
    public void publishIoTLightStatusUpdated(IoTLight light, IoTLightDirection direction) {
        UUID floorId = light.getCustomNode().getFloor().getId();
        IoTLightEventMessage<IoTLightStatusEventData> message = IoTLightEventMessage.of(
                TrainingEventType.IOT_LIGHT_STATUS_UPDATED,
                floorId,
                new IoTLightStatusEventData(light.getId(), direction, Instant.now())
        );

        messagingTemplate.convertAndSend(FLOOR_LIGHTS_TOPIC_PREFIX + floorId + FLOOR_LIGHTS_TOPIC_SUFFIX, message);

        log.debug(
                "유도등 상태 이벤트 발행: lightId={}, floorId={}, direction={}",
                light.getId(),
                floorId,
                direction
        );
    }

    // 훈련 상태 이벤트와 동일하게, DB 트랜잭션이 실제로 커밋된 이후에만 발행하고
    // 발행 실패는 로그만 남기고 삼킨다 (publishTrainingStatusUpdatedAfterCommit 참고).
    public void publishIoTLightStatusUpdatedAfterCommit(IoTLight light, IoTLightDirection direction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishIoTLightStatusUpdated(light, direction);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishIoTLightStatusUpdated(light, direction);
                        } catch (RuntimeException exception) {
                            log.error(
                                    "커밋 후 유도등 상태 이벤트 발행 실패: lightId={}",
                                    light.getId(),
                                    exception
                            );
                        }
                    }
                }
        );
    }
}