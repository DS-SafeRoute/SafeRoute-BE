package com.saferoute.domain.training.service;

import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.telemetry.dynamo.entity.LatestMonitoringCaptureItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.LatestMonitoringCaptureRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.dto.MonitoringEventListResponse;
import com.saferoute.domain.training.dto.MonitoringEventResponse;
import com.saferoute.domain.training.dto.MonitoringFrameListResponse;
import com.saferoute.domain.training.dto.MonitoringFrameResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
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
    private final RouteRecalculationRepository routeRecalculationRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final SchoolContextService schoolContextService;

    public MonitoringCameraListResponse getCameras(UUID sessionId, String email) {
        TrainingSession session = findRunningSessionForSchool(sessionId, email);
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

    public MonitoringFrameListResponse getFrames(
            UUID sessionId,
            UUID cctvId,
            int limit,
            String cursor,
            String email
    ) {
        TrainingSession session = findRunningSessionForSchool(sessionId, email);
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
        findRunningSessionForSchool(sessionId, email);

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

    private TrainingSession findRunningSessionForSchool(UUID sessionId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        TrainingSession session = trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
        if (session.getStatus() != TrainingStatus.RUNNING) {
            throw new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND);
        }
        return session;
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
