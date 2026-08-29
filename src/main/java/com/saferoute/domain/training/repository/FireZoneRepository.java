package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.FireZone;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FireZoneRepository extends JpaRepository<FireZone, UUID> {
    void deleteAllByFloor_Id(UUID floorId);

    // 현재 세대(frontier)에 속한 FireZone 조회 - 다음 확산의 시작점.
    List<FireZone> findByScenario_IdAndSpreadGeneration(UUID scenarioId, int spreadGeneration);

    // 세션 종료 시 해당 시나리오의 화재 셀들 isFired = false로 일괄 초기화
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE FloorGridCell c SET c.isFired = false
        WHERE c.id IN (
            SELECT fz.gridCell.id FROM FireZone fz WHERE fz.scenario.id = :scenarioId
        )
        AND c.id NOT IN (
            SELECT fz2.gridCell.id FROM FireZone fz2
            WHERE fz2.scenario.id <> :scenarioId
              AND fz2.scenario.id IN (
                  SELECT ts.scenario.id FROM TrainingSession ts
                  WHERE ts.status = com.saferoute.domain.training.entity.TrainingStatus.RUNNING
              )
        )
        """)
    void resetFiredCellsByScenarioId(@Param("scenarioId") UUID scenarioId);
}