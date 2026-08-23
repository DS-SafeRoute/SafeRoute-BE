package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.request.ReportObservationRequest;
import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.util.MonitoredAreaCalculator;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.telemetry.dynamo.entity.CurrentCctvStateItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CurrentCctvStateRepository;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Pi가 5초마다 보내는 관측값을 받아 BE가 직접 밀도·혼잡 단계를 계산하고 저장한다.
// 멱등 저장/처리 lease/after-commit 발행 구조는 CongestionEventService와 유사한 패턴을 쓴다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionObservationService {

    static final Duration PROCESSING_LEASE = Duration.ofMinutes(1);

    private final ObservationRepository observationRepository;
    private final CurrentCctvStateRepository currentCctvStateRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final CctvGridCellRepository cctvGridCellRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;
    private final CongestionConfigService congestionConfigService;
    private final RouteRecalculationService routeRecalculationService;
    private final TrainingEventPublisher trainingEventPublisher;

    @Transactional
    public IdempotentSaveResult<ObservationItem> reportObservation(Cctv cctv, ReportObservationRequest request) {
        Floor floor = cctv.getCustomNode().getFloor();
        UUID buildingId = floor.getBuilding().getId();
        TrainingSession session = trainingSessionRepository
                .findByIdAndStatusAndScenario_Building_Id(
                        request.trainingSessionId(), TrainingStatus.RUNNING, buildingId)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND));

        CongestionConfig config = congestionConfigService.getConfig();
        Double monitoredAreaM2 = MonitoredAreaCalculator.calculate(
                cctvGridCellRepository.countByCctv_Id(cctv.getId()), floor.getGridCellSizeMeter());
        // GridCell이 하나도 없으면 0.0㎡로 계산되는데, 그대로 나누면 density가 무한대가 되므로 여기서도 막는다.
        if (monitoredAreaM2 == null || monitoredAreaM2 <= 0) {
            throw new ApiException(CongestionErrorCode.MONITORED_AREA_NOT_AVAILABLE);
        }

        double density = request.avgHeadcount() / monitoredAreaM2;
        CongestionLevel level = config.classify(density);

        ObservationItem item = ObservationItem.create(
                request.eventId(),
                session.getId(),
                null,
                cctv.getCode(),
                request.avgHeadcount(),
                request.peakHeadcount(),
                request.sampleCount(),
                density,
                level,
                request.windowStart(),
                request.windowEnd(),
                request.capturedAt(),
                request.monitoringImageKey(),
                request.configVersion()
        );

        IdempotentSaveResult<ObservationItem> saveResult = observationRepository.saveIfAbsent(item);
        validateEventIdentity(saveResult.item(), request, session.getId());

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
            List<MapEdge> affectedEdges = resolveAffectedEdges(cctv.getId());
            updateCurrentState(session.getId(), cctv.getCode(), request, saveResult.item());

            CongestionLevel savedLevel = saveResult.item().getCongestionLevel();
            // 관측값은 5초 단위 스냅샷이라 STARTED/ENDED 같은 이벤트 구분이 없다 - 항상 LEVEL_UP으로 취급한다
            // (즉시 이벤트의 CONGESTION_ENDED 기반 복구 트리거는 CongestionEventService가 전담한다).
            if (savedLevel.requiresRouteRecalculation()) {
                for (MapEdge edge : affectedEdges) {
                    routeRecalculationService.trigger(
                            session, edge, savedLevel, RecalculationTriggerType.LEVEL_UP, cctv.getCode(), density);
                }
            }
            publishAndCompleteAfterCommit(session.getId(), affectedEdges, saveResult.item(), processingOwner);
        } catch (ApiException exception) {
            failProcessing(saveResult.item().getEventId(), processingOwner, exception);
            throw exception;
        } catch (RuntimeException exception) {
            failProcessing(saveResult.item().getEventId(), processingOwner, exception);
            throw new ApiException(CongestionErrorCode.EVENT_PROCESSING_FAILED, exception);
        }
        return saveResult;
    }

    // CCTV -> CctvGridCell -> FloorGridCell -> MapEdgeGridCell -> MapEdge 연쇄 조회.
    // CCTV 한 대가 여러 Edge를 감시할 수 있어 목록으로 반환한다 (Cctv.java 클래스 주석 참고).
    private List<MapEdge> resolveAffectedEdges(UUID cctvId) {
        List<UUID> gridCellIds = cctvGridCellRepository
                .findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctvId)
                .stream()
                .map(mapping -> mapping.getGridCell().getId())
                .toList();
        if (gridCellIds.isEmpty()) {
            return List.of();
        }
        return mapEdgeGridCellRepository.findAllByGridCell_IdIn(gridCellIds).stream()
                .map(MapEdgeGridCell::getMapEdge)
                .distinct()
                .toList();
    }

    private void updateCurrentState(
            UUID sessionId, String cctvCode, ReportObservationRequest request, ObservationItem item
    ) {
        CurrentCctvStateItem stateItem = CurrentCctvStateItem.create(
                sessionId,
                cctvCode,
                (int) Math.round(request.avgHeadcount()),
                item.getDensity(),
                item.getCongestionLevel(),
                request.capturedAt(),
                request.configVersion()
        );
        // 오래된 관측값이 최신 상태를 덮어쓰지 않도록 조건부 갱신 (lastDetectedAt 기준). 실패해도 관측값 저장 자체는 유지한다.
        if (!currentCctvStateRepository.updateIfLatest(stateItem)) {
            log.debug("더 최신 상태가 있어 CCTV 현재 상태 갱신을 건너뜀: cctvCode={}", cctvCode);
        }
    }

    private void validateEventIdentity(ObservationItem item, ReportObservationRequest request, UUID sessionId) {
        boolean sameIdentity = Objects.equals(item.getTrainingSessionId(), sessionId.toString())
                && Objects.equals(item.getCctvCode(), request.cctvCode());
        if (!sameIdentity) {
            throw new ApiException(CongestionErrorCode.EVENT_IDENTITY_MISMATCH);
        }
    }

    private void publishAndCompleteAfterCommit(
            UUID sessionId, List<MapEdge> affectedEdges, ObservationItem item, String processingOwner
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishCongestionUpdated(sessionId, affectedEdges, item);
            completeProcessing(item.getEventId(), processingOwner);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publishCongestionUpdated(sessionId, affectedEdges, item);
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

    // Edge가 없으면(감시 영역에 아직 GridCell/Edge 매핑이 안 된 CCTV) edgeId 없이 한 번만 발행해
    // 대시보드가 최소한 CCTV 단위 관측값은 볼 수 있게 한다.
    private void publishCongestionUpdated(UUID sessionId, List<MapEdge> affectedEdges, ObservationItem item) {
        if (affectedEdges.isEmpty()) {
            trainingEventPublisher.publishCongestionUpdated(sessionId, null, item);
            return;
        }
        for (MapEdge edge : affectedEdges) {
            trainingEventPublisher.publishCongestionUpdated(sessionId, edge.getId(), item);
        }
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
