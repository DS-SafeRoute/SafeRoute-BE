package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionEventService {

    static final Duration PROCESSING_LEASE = Duration.ofMinutes(1);

    private final MapEdgeJpaRepository mapEdgeJpaRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ObservationRepository observationRepository;
    private final TrainingEventPublisher trainingEventPublisher;
    private final RouteRecalculationService routeRecalculationService;

    // Raspberry Pi / YOLO가 보고하는 혼잡 이벤트를 받아 DynamoDB에 저장하고, CROWDED이면 재탐색을 트리거한다.
    // 대상 건물에 RUNNING 세션이 없으면 공통 에러 응답으로 처리한다.
    @Transactional
    public IdempotentSaveResult<ObservationItem> reportCongestion(ReportCongestionRequest request) {
        MapEdge edge = mapEdgeJpaRepository.findById(request.edgeId())
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.MAP_EDGE_NOT_FOUND));

        var buildingId = edge.getFloor().getBuilding().getId();
        TrainingSession session = trainingSessionRepository
                .findByIdAndStatusAndScenario_Building_Id(
                        request.trainingSessionId(),
                        TrainingStatus.RUNNING,
                        buildingId
                )
                .orElseThrow(() -> new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND));

        ObservationItem item = ObservationItem.create(
                request.eventId(),
                session.getId(),
                edge.getId(),
                request.cctvCode(),
                request.avgHeadcount(),
                request.peakHeadcount(),
                request.sampleCount(),
                request.density(),
                request.congestionLevel(),
                request.windowStart(),
                request.windowEnd(),
                request.capturedAt(),
                request.monitoringImageKey(),
                request.configVersion()
        );
        IdempotentSaveResult<ObservationItem> saveResult = observationRepository.saveIfAbsent(item);
        validateEventIdentity(saveResult.item(), request);
        String processingOwner = UUID.randomUUID().toString();
        long processingStartedAt = Instant.now().toEpochMilli();
        boolean claimed = observationRepository.claimProcessing(
                saveResult.item().getEventId(),
                processingOwner,
                processingStartedAt,
                processingStartedAt + PROCESSING_LEASE.toMillis()
        );
        if (!claimed) {
            return saveResult;
        }

        try {
            CongestionLevel level = saveResult.item().getCongestionLevel();
            if (level.requiresRouteRecalculation()) {
                routeRecalculationService.trigger(session, edge, level);
            }
            publishAndCompleteAfterCommit(
                    session.getId(),
                    edge.getId(),
                    saveResult.item(),
                    processingOwner
            );
        } catch (ApiException exception) {
            failProcessing(saveResult.item().getEventId(), processingOwner, exception);
            throw exception;
        } catch (RuntimeException exception) {
            failProcessing(saveResult.item().getEventId(), processingOwner, exception);
            throw new ApiException(CongestionErrorCode.EVENT_PROCESSING_FAILED, exception);
        }
        return saveResult;
    }

    private void validateEventIdentity(ObservationItem item, ReportCongestionRequest request) {
        boolean sameIdentity = Objects.equals(item.getTrainingSessionId(), request.trainingSessionId().toString())
                && Objects.equals(item.getEdgeId(), request.edgeId().toString())
                && Objects.equals(item.getCctvCode(), request.cctvCode());
        if (!sameIdentity) {
            throw new ApiException(CongestionErrorCode.EVENT_IDENTITY_MISMATCH);
        }
    }

    private void publishAndCompleteAfterCommit(
            UUID sessionId,
            UUID edgeId,
            ObservationItem item,
            String processingOwner
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            trainingEventPublisher.publishCongestionUpdated(sessionId, edgeId, item);
            completeProcessing(item.getEventId(), processingOwner);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    trainingEventPublisher.publishCongestionUpdated(sessionId, edgeId, item);
                    completeProcessing(item.getEventId(), processingOwner);
                } catch (RuntimeException exception) {
                    failProcessing(item.getEventId(), processingOwner, exception);
                    throw new ApiException(CongestionErrorCode.EVENT_PROCESSING_FAILED, exception);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    failProcessing(item.getEventId(), processingOwner, null);
                }
            }
        });
    }

    private void completeProcessing(String eventId, String processingOwner) {
        try {
            if (!observationRepository.completeProcessing(eventId, processingOwner)) {
                log.warn("혼잡 관측 처리 완료 상태 갱신 실패: eventId={}", eventId);
            }
        } catch (RuntimeException exception) {
            log.error("혼잡 관측 처리 완료 상태 저장 중 오류: eventId={}", eventId, exception);
        }
    }

    private void failProcessing(String eventId, String processingOwner, RuntimeException cause) {
        try {
            if (!observationRepository.failProcessing(eventId, processingOwner)) {
                log.warn("혼잡 관측 처리 실패 상태 갱신 실패: eventId={}", eventId);
            }
        } catch (RuntimeException statusException) {
            if (cause != null) {
                cause.addSuppressed(statusException);
            }
            log.error("혼잡 관측 처리 실패 상태 저장 중 오류: eventId={}", eventId, statusException);
        }
    }
}
