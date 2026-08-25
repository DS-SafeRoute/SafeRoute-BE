package com.saferoute.domain.evacuation.recalculation.repository;

import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRecalculationRepository extends JpaRepository<RouteRecalculation, UUID> {

    // Pi가 혼잡 이벤트를 짧은 주기로 반복 전송하므로, 같은 세션+엣지에 대해
    // 이미 PENDING이 있으면 그 요청을 조회해 레벨이 같으면 무시하고, 다르면 취소 후 새로 만든다.
    Optional<RouteRecalculation> findByTrainingSession_IdAndTriggerEdge_IdAndStatus(
            UUID trainingSessionId, UUID triggerEdgeId, RecalculationStatus status);

    // 같은 세션+엣지에서 가장 최근에 승인된 경로 - "현재 활성 경로"의 대용으로 쓴다
    // (이 시스템엔 활성 경로를 별도로 저장하는 개념이 없음).
    Optional<RouteRecalculation> findFirstByTrainingSession_IdAndTriggerEdge_IdAndStatusOrderByResolvedAtDesc(
            UUID trainingSessionId, UUID triggerEdgeId, RecalculationStatus status);

    List<RouteRecalculation> findAllByTrainingSession_IdOrderByRequestedAtDesc(UUID trainingSessionId);

    List<RouteRecalculation>
    findAllByTrainingSession_IdAndTrainingSession_Scenario_Building_SchoolNameOrderByRequestedAtDesc(
            UUID trainingSessionId, String schoolName);

    List<RouteRecalculation> findAllByTrainingSession_IdAndStatusOrderByRequestedAtDesc(
            UUID trainingSessionId, RecalculationStatus status);

    List<RouteRecalculation>
    findAllByTrainingSession_IdAndStatusAndTrainingSession_Scenario_Building_SchoolNameOrderByRequestedAtDesc(
            UUID trainingSessionId, RecalculationStatus status, String schoolName);

    Optional<RouteRecalculation> findByIdAndTrainingSession_Scenario_Building_SchoolName(
            UUID id, String schoolName);

    // 훈련 종료 시 해당 세션의 남은 PENDING을 일괄 무효화하기 위한 조회
    List<RouteRecalculation> findAllByTrainingSession_IdAndStatus(UUID trainingSessionId, RecalculationStatus status);
}
