package com.saferoute.domain.report.service;

import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.repository.CongestionConfigRepository;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.UserZone;
import com.saferoute.domain.report.entity.CumulativeEvacuationPoint;
import com.saferoute.domain.report.entity.RecentEvacuationPoint;
import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.report.entity.ZoneDensityPoint;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// 훈련 리포트 페이지의 3개 차트 데이터를 계산
@Service
@RequiredArgsConstructor
public class TrainingReportChartService {

    // CongestionConfig가 아직 초기화되지 않은 예외적인 경우의 대체값 (CongestionConfig.createDefault()와 동일).
    private static final double DEFAULT_VERY_CROWDED_FROM = 5.0;
    private static final int OBSERVATION_QUERY_LIMIT = 5_000;
    private static final int CUMULATIVE_BUCKET_INTERVAL_SEC = 30;
    // 최근 5회 차트 = 과거 리포트 4개 + 이번 리포트 1개
    private static final int RECENT_HISTORY_LIMIT = 4;

    private final CctvJpaRepository cctvJpaRepository;
    private final CctvGridCellRepository cctvGridCellRepository;
    private final ObservationRepository observationRepository;
    private final CongestionConfigRepository congestionConfigRepository;
    private final TrainingReportRepository trainingReportRepository;

    // 구역(UserZone)별 평균 밀집도. CCTV가 매핑된 그리드 셀의 소속 구역을 기준으로 묶는다.
    public List<ZoneDensityPoint> buildZoneDensities(TrainingSession session) {
        List<Cctv> cctvs = findBuildingCctvs(session);
        if (cctvs.isEmpty()) {
            return List.of();
        }

        double veryCrowdedFrom = congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID)
                .map(CongestionConfig::getVeryCrowdedFrom)
                .orElse(DEFAULT_VERY_CROWDED_FROM);

        Map<String, List<Double>> densitiesByZone = new LinkedHashMap<>();
        for (Cctv cctv : cctvs) {
            Set<String> zoneNames = cctvGridCellRepository
                    .findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctv.getId())
                    .stream()
                    .map(CctvGridCell::getGridCell)
                    .map(FloorGridCell::getUserZone)
                    .filter(Objects::nonNull)
                    .map(UserZone::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (zoneNames.isEmpty()) {
                continue;
            }

            List<ObservationItem> observations = observationRepository.findAllBySessionIdAndCctvCode(
                    session.getId().toString(), cctv.getCode(), OBSERVATION_QUERY_LIMIT);
            double avgDensity = observations.stream()
                    .map(ObservationItem::getDensity)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            for (String zoneName : zoneNames) {
                densitiesByZone.computeIfAbsent(zoneName, key -> new ArrayList<>()).add(avgDensity);
            }
        }

        return densitiesByZone.entrySet().stream()
                .map(entry -> {
                    double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double percent = Math.min(100.0, avg / veryCrowdedFrom * 100.0);
                    return new ZoneDensityPoint(entry.getKey(), Math.round(percent * 10) / 10.0);
                })
                .toList();
    }

    // 건물 전체 CCTV가 탐지한 인원 합을 참여 인원에서 뺀 값을 "탈출 완료 추정 인원"으로 근사한다.
    // 개별 탈출 확인 장치가 없어 낼 수 있는 최선의 근사치이며, 정확한 실측값이 아니다.
    public List<CumulativeEvacuationPoint> buildCumulativeEvacuation(TrainingSession session, int participantCount) {
        List<Cctv> cctvs = findBuildingCctvs(session);
        if (cctvs.isEmpty() || session.getEndedAt() == null) {
            return List.of();
        }

        Map<String, List<ObservationItem>> observationsByCctv = new HashMap<>();
        int totalObservations = 0;
        for (Cctv cctv : cctvs) {
            List<ObservationItem> observations = observationRepository.findAllBySessionIdAndCctvCode(
                    session.getId().toString(), cctv.getCode(), OBSERVATION_QUERY_LIMIT);
            observationsByCctv.put(cctv.getCode(), observations);
            totalObservations += observations.size();
        }
        if (totalObservations == 0) {
            return List.of();
        }

        long startMillis = session.getStartedAt().toEpochMilli();
        long endMillis = session.getEndedAt().toEpochMilli();
        long durationSec = Math.max(0, (endMillis - startMillis) / 1000);

        List<CumulativeEvacuationPoint> points = new ArrayList<>();
        int runningMax = 0;
        for (long elapsedSec = 0; elapsedSec <= durationSec; elapsedSec += CUMULATIVE_BUCKET_INTERVAL_SEC) {
            runningMax = appendCumulativePoint(
                    points, observationsByCctv, startMillis, elapsedSec, participantCount, runningMax);
        }
        // durationSec이 버킷 간격의 배수가 아니면(예: 45초) 위 루프가 세션 종료 시점을 건너뛴다.
        // 마지막 상태(세션 종료 시점의 누적 대피 인원)를 놓치지 않도록 별도로 한 번 더 채운다.
        if (points.isEmpty() || points.get(points.size() - 1).getElapsedSec() != durationSec) {
            appendCumulativePoint(
                    points, observationsByCctv, startMillis, durationSec, participantCount, runningMax);
        }
        return points;
    }

    private int appendCumulativePoint(List<CumulativeEvacuationPoint> points,
                                       Map<String, List<ObservationItem>> observationsByCctv,
                                       long startMillis, long elapsedSec, int participantCount, int runningMax) {
        long bucketMillis = startMillis + elapsedSec * 1000;
        double totalHeadcount = 0.0;
        for (List<ObservationItem> observations : observationsByCctv.values()) {
            totalHeadcount += headcountAt(observations, bucketMillis);
        }
        int estimatedRemaining = (int) Math.round(totalHeadcount);
        int cumulativeEvacuated = Math.max(0, Math.min(participantCount, participantCount - estimatedRemaining));
        // 센서 노이즈로 탐지 인원이 일시적으로 늘어 보여도, 누적 대피 인원은 절대 줄어들지 않게 한다.
        int newRunningMax = Math.max(runningMax, cumulativeEvacuated);
        points.add(new CumulativeEvacuationPoint((int) elapsedSec, newRunningMax));
        return newRunningMax;
    }

    // 같은 건물에서 과거에 생성된 리포트(최대 4개, 오래된 순)에 이번 리포트의 대피시간을 마지막으로 붙인다.
    public List<RecentEvacuationPoint> buildRecentEvacuationTimes(UUID buildingId, int currentEvacuationSec) {
        List<TrainingReport> history = trainingReportRepository
                .findByTrainingSession_Scenario_Building_IdOrderByCreatedAtDesc(
                        buildingId, PageRequest.of(0, RECENT_HISTORY_LIMIT));

        List<Integer> secondsOldestFirst = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            secondsOldestFirst.add(history.get(i).getAvgEvacuationSec());
        }
        secondsOldestFirst.add(currentEvacuationSec);

        List<RecentEvacuationPoint> points = new ArrayList<>();
        for (int i = 0; i < secondsOldestFirst.size(); i++) {
            points.add(new RecentEvacuationPoint(i + 1, secondsOldestFirst.get(i)));
        }
        return points;
    }

    private List<Cctv> findBuildingCctvs(TrainingSession session) {
        UUID buildingId = session.getScenario().getBuildingId();
        return cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId);
    }

    // observations는 findAllBySessionIdAndCctvCode의 시간 오름차순 정렬을 그대로 사용한다.
    // 첫 관측 이전 구간은 첫 관측값으로, 그 이후는 해당 시각 이전의 마지막 관측값으로 근사한다.
    private double headcountAt(List<ObservationItem> observations, long timestampMillis) {
        if (observations.isEmpty()) {
            return 0.0;
        }
        double latest = observations.get(0).getAvgHeadcount() != null ? observations.get(0).getAvgHeadcount() : 0.0;
        for (ObservationItem observation : observations) {
            if (observation.getCapturedAt() == null || observation.getCapturedAt() > timestampMillis) {
                break;
            }
            if (observation.getAvgHeadcount() != null) {
                latest = observation.getAvgHeadcount();
            }
        }
        return latest;
    }
}
