package com.saferoute.infrastructure.websocket.service;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.infrastructure.websocket.dto.TrainingEventMessage;
import com.saferoute.infrastructure.websocket.dto.TrainingEventType;
import com.saferoute.infrastructure.websocket.dto.TrainingStatusEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 관리자 대시보드(/topic/training-sessions/{sessionId})로 보내는 WebSocket 이벤트 발행 책임을 이 클래스가 전담한다.
// SimpMessagingTemplate을 다른 서비스에 직접 주입하지 않고 항상 이 클래스를 거친다.
//
// 혼잡도 / 경로 재계산 / 유도등 상태 이벤트 발행 메서드는 아직 없다.
// 대응하는 도메인 로직이 만들어지면 이 클래스에 전용 메서드를 추가한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingEventPublisher {

    private static final String SESSION_TOPIC_PREFIX = "/topic/training-sessions/";

    private final SimpMessagingTemplate messagingTemplate;

    // 현재 이 메서드를 호출하는 지점은 없다.
    // TrainingSession.complete()/stop()/fail() 을 호출하는 훈련 종료 API가 추가되면
    // 트랜잭션 커밋 이후 이 메서드(또는 publishTrainingStatusUpdatedAfterCommit)를 호출해 연동한다.
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
    public void publishTrainingStatusUpdatedAfterCommit(TrainingSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishTrainingStatusUpdated(session);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishTrainingStatusUpdated(session);
                    }
                }
        );
    }
}