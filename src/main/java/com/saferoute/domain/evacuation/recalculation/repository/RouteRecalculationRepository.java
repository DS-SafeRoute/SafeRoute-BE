package com.saferoute.domain.evacuation.recalculation.repository;

import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRecalculationRepository extends JpaRepository<RouteRecalculation, UUID> {

    // Pi가 혼잡 이벤트를 짧은 주기로 반복 전송하므로, 같은 세션+엣지에 대해
    // 이미 PENDING이 있으면 새로 트리거하지 않는다.
    boolean existsByTrainingSession_IdAndTriggerEdge_IdAndStatus(
            UUID trainingSessionId, UUID triggerEdgeId, RecalculationStatus status);
}
