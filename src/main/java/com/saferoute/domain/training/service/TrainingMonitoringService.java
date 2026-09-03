package com.saferoute.domain.training.service;

import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.service.CongestionConfigService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.telemetry.dynamo.entity.CurrentCctvStateItem;
import com.saferoute.domain.telemetry.dynamo.entity.LatestMonitoringCaptureItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.CurrentCctvStateRepository;
import com.saferoute.domain.telemetry.dynamo.repository.LatestMonitoringCaptureRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.dto.CurrentCctvStateListResponse;
import com.saferoute.domain.training.dto.CurrentCctvStateResponse;
import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.dto.MonitoringContextResponse;
import com.saferoute.domain.training.dto.MonitoringEventListResponse;
import com.saferoute.domain.training.dto.MonitoringEventResponse;
import com.saferoute.domain.training.dto.MonitoringFrameListResponse;
import com.saferoute.domain.training.dto.MonitoringFrameResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingMonitoringService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final CctvJpaRepository cctvJpaRepository;
    private final LatestMonitoringCaptureRepository latestMonitoringCaptureRepository;
    private final ObservationRepository observationRepository;
    private final CongestionEventRepository congestionEventRepository;
    private final CurrentCctvStateRepository currentCctvStateRepository;
    private final RouteRecalculationRepository routeRecalculationRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final SchoolContextService schoolContextService;
    private final CongestionConfigService congestionConfigService;

    public MonitoringCameraListResponse getCameras(UUID sessionId, String email) {
        TrainingSession session = findSessionForSchool(sessionId, email);
        UUID buildingId = session.getScenario().getBuilding().getId();
        List<Cctv> cctvs = cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId);
        if (cctvs.isEmpty()) {
            return new MonitoringCameraListResponse(sessionId, List.of());
        }
        Map<String, LatestMonitoringCaptureItem> capturesByCctvCode =
                latestMonitoringCaptureRepository.findAllBySessionId(sessionId.toString()).stream()
                        .collect(Collectors.toMap(
                                LatestMonitoringCaptureItem::getCctvCode,
                                Function.identity()
                        ));

        List<MonitoringCameraResponse> cameras = cctvs.stream()
                .map(cctv -> toResponse(cctv, capturesByCctvCode.get(cctv.getCode())))
                .toList();
        return new MonitoringCameraListResponse(sessionId, cameras);
    }

    // 모니터링 상세 화면 헤더에 필요한 세션 기본 정보 + 전역 설정값(저장 간격, stale 기준)을
    // 한 번에 제공한다. 다른 모니터링 조회 API와 동일하게 세션 상태와 무관하게 조회 가능하다.
    // 아직 시작 전(SCHEDULED)인 세션은 startedAt/elapsedSeconds가 모두 null이다.
    public MonitoringContextResponse getContext(UUID sessionId, String email) {
        TrainingSession session = findSessionForSchool(sessionId, email);
        CongestionConfig config = congestionConfigService.getConfig();
        Instant startedAt = session.getStartedAt();
        Instant endedAt = session.getEndedAt();
        Long elapsedSeconds = startedAt == null
                ? null
                : Duration.between(startedAt, endedAt != null ? endedAt : Instant.now()).getSeconds();

        return new MonitoringContextResponse(
                session.getId(),
                session.getScenario().getName(),
                session.getScenario().getBuilding().getName(),
                session.getStatus(),
                startedAt != null ? startedAt.toEpochMilli() : null,
                endedAt != null ? endedAt.toEpochMilli() : null,
                elapsedSeconds,
                config.getSnapshotIntervalSec(),
                config.getStateStaleAfterSec()
        );
    }

    // 상태가 아직 없는 CCTV도 목록에서 누락하지 않고 stale=true인 null 상태로 반환한다.
    // 기준 데이터가 5초 주기 Observation이므로, 마지막 관측 이후 stateStaleAfterSec가 지났으면
    // congestionLevel 등이 남아있어도 stale=true로 표시해 "오래된 정보를 NORMAL로 오인"하는 것을 막는다.
    public CurrentCctvStateListResponse getCurrentStates(UUID sessionId, String email) {
        TrainingSession session = findSessionForSchool(sessionId, email);
        UUID buildingId = session.getScenario().getBuilding().getId();
        List<Cctv> cctvs = cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId);
        long observedAt = Instant.now().toEpochMilli();
        if (cctvs.isEmpty()) {
            return new CurrentCctvStateListResponse(sessionId, observedAt, List.of());
        }

        int stateStaleAfterSec = congestionConfigService.getConfig().getStateStaleAfterSec();
        Map<String, CurrentCctvStateItem> statesByCctvCode =
                currentCctvStateRepository.findAllBySessionId(sessionId.toString()).stream()
                        .collect(Collectors.toMap(
                                CurrentCctvStateItem::getCctvCode,
                                Function.identity()
                        ));

        List<CurrentCctvStateResponse> states = cctvs.stream()
                .map(cctv -> toStateResponse(cctv, statesByCctvCode.get(cctv.getCode()), observedAt, stateStaleAfterSec))
                .toList();
        return new CurrentCctvStateListResponse(sessionId, observedAt, states);
    }

    private CurrentCctvStateResponse toStateResponse(
            Cctv cctv, CurrentCctvStateItem item, long observedAt, int stateStaleAfterSec
    ) {
        if (item == null) {
            return CurrentCctvStateResponse.withoutState(cctv);
        }
        boolean stale = observedAt - item.getLastDetectedAt() > stateStaleAfterSec * 1_000L;
        return CurrentCctvStateResponse.withState(cctv, item, stale);
    }

    public MonitoringFrameListResponse getFrames(
            UUID sessionId,
            UUID cctvId,
            int limit,
            String cursor,
            String email
    ) {
        TrainingSession session = findSessionForSchool(sessionId, email);
        Cctv cctv = findCctvInSessionBuilding(cctvId, session);
        FrameCursor.Position position = FrameCursor.decode(cursor);
        Long beforeCapturedAt = position != null ? position.capturedAt() : null;
        String beforeEventId = position != null ? position.eventId() : null;

        // hasNext 판단을 위해 요청한 개수보다 하나 더 조회한다.
        List<ObservationItem> page = observationRepository.findPageBySessionIdAndCctvCode(
                sessionId.toString(), cctv.getCode(), limit + 1, beforeCapturedAt, beforeEventId);

        boolean hasNext = page.size() > limit;
        List<ObservationItem> items = hasNext ? page.subList(0, limit) : page;

        List<MonitoringFrameResponse> frames = items.stream()
                .map(this::toFrameResponse)
                .toList();
        String nextCursor = hasNext
                ? FrameCursor.encode(
                        items.get(items.size() - 1).getCapturedAt(),
                        items.get(items.size() - 1).getEventId())
                : null;

        return new MonitoringFrameListResponse(sessionId, cctvId, frames, nextCursor, hasNext);
    }

    // 혼잡 감지 이벤트(CongestionEventItem)와 경로 재탐색 이벤트(RouteRecalculation)를 발생 시각순으로 합친다.
    // 관측값(ObservationItem)은 5초 주기 스냅샷이라 타임라인에 넣으면 너무 촘촘해져서 제외한다 -
    // "이벤트"로 부를 만한 STARTED/LEVEL_UP/ENDED 전환만 대상으로 한다.
    // 재탐색 한 건은 요청 시점과(있다면) 해소 시점 두 항목으로 나뉠 수 있다.
    public MonitoringEventListResponse getEvents(UUID sessionId, String cctvCode, String email) {
        findSessionForSchool(sessionId, email);

        List<MonitoringEventResponse> events = new ArrayList<>();
        congestionEventRepository.findAllBySessionId(sessionId.toString()).stream()
                .filter(item -> matchesCctv(item.getCctvCode(), cctvCode))
                .map(MonitoringEventResponse::fromCongestionEvent)
                .forEach(events::add);

        routeRecalculationRepository.findAllByTrainingSession_IdOrderByRequestedAtDesc(sessionId).stream()
                .filter(recalculation -> matchesCctv(recalculation.getCctvCode(), cctvCode))
                .forEach(recalculation -> addRecalculationEvents(events, recalculation));

        events.sort(Comparator.comparingLong(MonitoringEventResponse::occurredAt));
        return new MonitoringEventListResponse(sessionId, events);
    }

    private void addRecalculationEvents(List<MonitoringEventResponse> events, RouteRecalculation recalculation) {
        events.add(MonitoringEventResponse.requestedFrom(recalculation));
        if (recalculation.getResolvedAt() != null) {
            events.add(MonitoringEventResponse.resolvedFrom(recalculation));
        }
    }

    private boolean matchesCctv(String eventCctvCode, String filterCctvCode) {
        return filterCctvCode == null || Objects.equals(eventCctvCode, filterCctvCode);
    }

    private Cctv findCctvInSessionBuilding(UUID cctvId, TrainingSession session) {
        UUID buildingId = session.getScenario().getBuilding().getId();
        return cctvJpaRepository.findByIdAndCustomNode_Floor_Building_Id(cctvId, buildingId)
                .orElseThrow(() -> new ApiException(CctvErrorCode.CCTV_NOT_FOUND));
    }

    private MonitoringFrameResponse toFrameResponse(ObservationItem item) {
        if (item.getMonitoringImageKey() == null || item.getMonitoringImageKey().isBlank()) {
            return MonitoringFrameResponse.withoutImage(item);
        }
        PresignedGetUrl presignedGetUrl = s3PresignedUrlService.createGetUrl(item.getMonitoringImageKey());
        return MonitoringFrameResponse.withImage(item, presignedGetUrl);
    }

    // 읽기 전용 조회이므로 세션 상태는 검증하지 않는다 - 존재 여부와 요청자 학교 소속만 확인한다.
    // 종료된(COMPLETED/STOPPED/FAILED) 세션도 마지막 저장된 데이터를 그대로 조회할 수 있어야
    // 훈련 종료 직후나 새로고침 후에도 화면이 깨지지 않는다. Pi가 데이터를 보내는 쓰기 경로
    // (CongestionObservationService/CongestionEventService)는 이 정책과 무관하게 RUNNING만 계속 허용한다.
    private TrainingSession findSessionForSchool(UUID sessionId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        return trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
    }

    private MonitoringCameraResponse toResponse(
            Cctv cctv,
            LatestMonitoringCaptureItem capture
    ) {
        if (!hasMonitoringImage(capture)) {
            return MonitoringCameraResponse.withoutCapture(cctv);
        }
        return withCapture(cctv, capture);
    }

    private boolean hasMonitoringImage(LatestMonitoringCaptureItem capture) {
        return capture != null
                && capture.getMonitoringImageKey() != null
                && !capture.getMonitoringImageKey().isBlank();
    }

    private MonitoringCameraResponse withCapture(Cctv cctv, LatestMonitoringCaptureItem capture) {
        PresignedGetUrl presignedGetUrl =
                s3PresignedUrlService.createGetUrl(capture.getMonitoringImageKey());
        return MonitoringCameraResponse.withCapture(
                cctv,
                capture.getCapturedAt(),
                presignedGetUrl
        );
    }
}
