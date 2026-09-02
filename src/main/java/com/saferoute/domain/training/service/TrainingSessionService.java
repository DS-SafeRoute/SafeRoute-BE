package com.saferoute.domain.training.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.recalculation.dto.response.CurrentRouteResponse;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.RunningSessionResponse;
import com.saferoute.domain.training.dto.ScheduledSessionResponse;
import com.saferoute.domain.training.dto.TrainingSessionListResponse;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.dto.TrainingSessionSummaryResponse;
import com.saferoute.domain.training.dto.TrainingStatusResponse;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSessionService {

  // 텔레메트리(personCount) 수신 파이프라인이 아직 없어, 3분 공백 기반 정밀 타임아웃 판정 대신
  // 1단계로 RUNNING 세션에 대한 단순 하드 타임아웃만 적용한다. 카메라 연동 완료 후 별도 이슈에서 대체 예정.
  public static final Duration TRAINING_TIMEOUT = Duration.ofMinutes(10);

  private final UserRepository userRepository;
  private final TrainingSessionRepository trainingSessionRepository;
  private final TrainingScenarioRepository trainingScenarioRepository;
  private final FireZoneRepository fireZoneRepository;
  private final RouteRecalculationService routeRecalculationService;
  private final EvacuationRouteService evacuationRouteService;
  private final IoTLightService ioTLightService;
  private final TrainingEventPublisher trainingEventPublisher;
  private final SchoolContextService schoolContextService;
  private final BuildingRepository buildingRepository;

  @Transactional
  public TrainingSessionResponse create(CreateSessionRequest request, UUID scenarioId, String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    User user = userRepository.findByIdAndSchoolName(request.getAdminId(), schoolName)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.ADMIN_NOT_FOUND));
    if (user.getRole() != UserRole.MANAGER) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    TrainingScenario scenario = trainingScenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, schoolName)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND));

    if (trainingSessionRepository.existsByScenario_Id(scenarioId)) {
      throw new ApiException(TrainingErrorCode.SESSION_ALREADY_EXISTS);
    }

    TrainingSession trainingSession = TrainingSession.schedule(user, scenario);

    // existsByScenario_Id 체크와 save는 원자적이지 않아 동시 요청이 둘 다 통과할 수 있다.
    // 이 경우 뒤늦게 실패하는 쪽은 UNIQUE 제약 위반으로 걸러지므로, saveAndFlush로 INSERT를
    // 즉시 실행시켜 그 예외를 여기서 도메인 에러로 변환한다.
    try {
      return TrainingSessionResponse.from(trainingSessionRepository.saveAndFlush(trainingSession));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(TrainingErrorCode.SESSION_ALREADY_EXISTS);
    }
  }

  // 모니터링 화면 진입점: 프론트가 이 목록에서 sessionId를 얻어 모니터링 화면으로 이동한다.
  @Transactional(readOnly = true)
  public TrainingSessionListResponse getSessions(TrainingStatus status, String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    List<TrainingSession> trainingSessions = status == TrainingStatus.SCHEDULED
        ? trainingSessionRepository
            .findAllByStatusAndScenario_Building_SchoolNameOrderByCreatedAtDesc(status, schoolName)
        : trainingSessionRepository
            .findAllByStatusAndScenario_Building_SchoolNameOrderByStartedAtDesc(status, schoolName);
    List<TrainingSessionSummaryResponse> sessions = trainingSessions
        .stream()
        .map(TrainingSessionSummaryResponse::from)
        .toList();
    return new TrainingSessionListResponse(sessions);
  }

  @Transactional(readOnly = true)
  public TrainingStatusResponse getTrainingStatus(UUID sessionId, String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    TrainingSession session = trainingSessionRepository
        .findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

    TrainingScenario scenario = session.getScenario();
    Building building = scenario.getBuilding();

    if (session.getStatus() == TrainingStatus.SCHEDULED) {
      return new ScheduledSessionResponse(
          building.getName(),
          building.getTotalFloors(),
          scenario.getScheduledAt(),
          scenario.getExpectedParticipants()
      );
    } else if (session.getStatus() == TrainingStatus.RUNNING) {
      long elapsed = Instant.now().getEpochSecond() - session.getStartedAt().getEpochSecond();
      return new RunningSessionResponse(
          building.getName(),
          elapsed,
          session.getActualParticipants() != null ? session.getActualParticipants() : 0,
          session.getCurrentSurvivalRate() != null ? session.getCurrentSurvivalRate() : BigDecimal.ZERO
      );
    }
    throw new ApiException(TrainingErrorCode.UNSUPPORTED_STATUS);
  }

  @Transactional
  public TrainingSessionResponse start(UUID sessionId, String email) {
    TrainingSession session = findSession(sessionId, email);

    if (session.getStatus() != TrainingStatus.SCHEDULED) {
      throw new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION);
    }

    if (hasRunningSession(session.getScenario().getBuildingId())) {
      throw new ApiException(TrainingErrorCode.RUNNING_SESSION_ALREADY_EXISTS);
    }

    // 유도등 반영 전 최초 경로부터 계산해, EXIT 미지정/도달 불가 시 세션 상태를 바꾸지 않고 막는다.
    MapNode startNode = session.getScenario().getStartNode();
    if (startNode == null) {
      throw new ApiException(TrainingErrorCode.START_NODE_NOT_CONFIGURED);
    }
    if (startNode.getType() != NodeType.START) {
      throw new ApiException(TrainingErrorCode.START_NODE_TYPE_INVALID);
    }
    List<FireZone> fireOrigins = fireZoneRepository
        .findByScenario_IdAndIsManualAddTrue(session.getScenario().getId());
    if (fireOrigins.isEmpty()) {
      throw new ApiException(TrainingErrorCode.FIRE_ORIGIN_NOT_CONFIGURED);
    }
    UUID startFloorId = startNode.getFloor().getId();
    if (fireOrigins.stream().anyMatch(origin -> !startFloorId.equals(origin.getFloorId()))) {
      throw new ApiException(TrainingErrorCode.FIRE_ORIGIN_START_FLOOR_MISMATCH);
    }
    EvacuationRoute initialRoute =
        evacuationRouteService.findShortestRoute(startFloorId, startNode.getId());

    session.start(Instant.now());
    session.getScenario().markInProgress();
    ioTLightService.applyRouteGuidance(initialRoute.path().stream().map(MapNode::getId).toList());
    trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);

    return TrainingSessionResponse.from(session);
  }

  @Transactional
  public TrainingSessionResponse end(UUID sessionId, String email) {
    TrainingSession session = findSession(sessionId, email);

    if (session.getStatus() != TrainingStatus.RUNNING) {
      throw new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION);
    }

    session.complete(Instant.now());
    session.getScenario().markCompleted();
    fireZoneRepository.resetFiredCellsByScenarioId(session.getScenario().getId());
    routeRecalculationService.cancelAllPendingForSession(session.getId(), "훈련 종료로 무효화됨");
    ioTLightService.resetToNormal(session.getScenario().getBuildingId());
    trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);

    return TrainingSessionResponse.from(session);
  }

  // 관리자가 훈련을 중간에 강제로 끊는 경우로, 정상 종료(COMPLETED)와 구분해 시나리오도 ERROR로 표시한다.
  @Transactional
  public TrainingSessionResponse forceEnd(UUID sessionId, String email) {
    TrainingSession session = findSession(sessionId, email);

    if (session.getStatus() != TrainingStatus.RUNNING) {
      throw new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION);
    }

    session.stop(Instant.now());
    session.getScenario().markError();
    fireZoneRepository.resetFiredCellsByScenarioId(session.getScenario().getId());
    routeRecalculationService.cancelAllPendingForSession(session.getId(), "훈련 강제 종료로 무효화됨");
    ioTLightService.resetToNormal(session.getScenario().getBuildingId());
    trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);

    return TrainingSessionResponse.from(session);
  }

  // 10분 하드 타임아웃을 넘긴 RUNNING 세션을 스케줄러가 주기적으로 호출해 FAILED 처리한다.
  @Transactional
  public void failTimedOutSessions() {
    Instant threshold = Instant.now().minus(TRAINING_TIMEOUT);
    List<TrainingSession> timedOutSessions =
        trainingSessionRepository.findByStatusAndStartedAtBefore(TrainingStatus.RUNNING, threshold);

    for (TrainingSession session : timedOutSessions) {
      session.fail(Instant.now());
      session.getScenario().markError();
    }

    timedOutSessions.stream()
        .map(session -> session.getScenario().getId())
        .distinct()
        .forEach(fireZoneRepository::resetFiredCellsByScenarioId);

    for (TrainingSession session : timedOutSessions) {
      routeRecalculationService.cancelAllPendingForSession(session.getId(), "훈련 타임아웃으로 무효화됨");
      ioTLightService.resetToNormal(session.getScenario().getBuildingId());
      trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);
      log.info("훈련 세션 타임아웃 처리: sessionId={}", session.getId());
    }
  }

  // "지금 안내되고 있는 경로" 조회는 재탐색 승인 이력까지 함께 봐야 해서 실제 로직은
  // RouteRecalculationService가 갖고 있다 - 이 메서드는 /api/v1/sessions 하위 URL 컨벤션에
  // 맞추기 위한 위임일 뿐이다.
  @Transactional(readOnly = true)
  public CurrentRouteResponse getCurrentRoute(UUID sessionId, String email) {
    return routeRecalculationService.getCurrentRoute(sessionId, email);
  }

  private TrainingSession findSession(UUID sessionId, String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    return trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
  }

  private boolean hasRunningSession(UUID buildingId) {
    // 건물 행 잠금 이후 RUNNING 여부를 검사해야 동시 시작 요청도 하나만 성공한다.
    buildingRepository.findByIdForUpdate(buildingId);
    return trainingSessionRepository.existsByStatusAndScenario_Building_Id(
        TrainingStatus.RUNNING, buildingId);
  }
}
