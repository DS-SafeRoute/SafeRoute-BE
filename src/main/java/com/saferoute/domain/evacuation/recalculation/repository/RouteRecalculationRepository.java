package com.saferoute.domain.evacuation.recalculation.repository;

import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRecalculationRepository extends JpaRepository<RouteRecalculation, UUID> {

    @Query("""
            select r.trainingSession.id
            from RouteRecalculation r
            where r.id = :id
              and r.trainingSession.scenario.building.schoolName = :schoolName
            """)
    Optional<UUID> findTrainingSessionIdByIdAndSchoolName(
            @Param("id") UUID id, @Param("schoolName") String schoolName);

    // 세션 전체에서 가장 최근에 승인된 경로를 현재 활성 경로로 사용한다.
    Optional<RouteRecalculation> findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(
            UUID trainingSessionId, RecalculationStatus status);

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
