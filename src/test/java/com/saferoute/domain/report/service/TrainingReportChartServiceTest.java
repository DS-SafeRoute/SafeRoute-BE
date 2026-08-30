package com.saferoute.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.repository.CongestionConfigRepository;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.UserZone;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.report.entity.CumulativeEvacuationPoint;
import com.saferoute.domain.report.entity.RecentEvacuationPoint;
import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.report.entity.ZoneDensityPoint;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrainingReportChartServiceTest {

    @InjectMocks
    private TrainingReportChartService chartService;

    @Mock
    private CctvJpaRepository cctvJpaRepository;

    @Mock
    private CctvGridCellRepository cctvGridCellRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private CongestionConfigRepository congestionConfigRepository;

    @Mock
    private TrainingReportRepository trainingReportRepository;

    private final UUID buildingId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private Floor floor;

    private TrainingSession sessionOf(Instant startedAt, Instant endedAt) {
        floor = mock(Floor.class);
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getBuildingId()).willReturn(buildingId);
        TrainingSession session = mock(TrainingSession.class);
        // 구역 매핑이 없는 CCTV는 session.getId()까지 가지 않고 continue되므로, 이 스텁들은 lenient로 둔다.
        org.mockito.Mockito.lenient().when(session.getId()).thenReturn(sessionId);
        given(session.getScenario()).willReturn(scenario);
        org.mockito.Mockito.lenient().when(session.getStartedAt()).thenReturn(startedAt);
        org.mockito.Mockito.lenient().when(session.getEndedAt()).thenReturn(endedAt);
        return session;
    }

    private Cctv cctv(String code) {
        MapNode node = MapNode.createCustom(floor, code, code, 0, 0);
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        Cctv cctv = Cctv.create(code, code, node);
        ReflectionTestUtils.setField(cctv, "id", UUID.randomUUID());
        return cctv;
    }

    private FloorGridCell cellInZone(String zoneName) {
        FloorGridCell cell = FloorGridCell.create(floor, 0, 0, true, 0.0, 0.0);
        if (zoneName != null) {
            UserZone zone = UserZone.create(floor, zoneName);
            cell.assignUserZone(zone);
        }
        return cell;
    }

    private ObservationItem observation(String cctvCode, double avgHeadcount, double density, long capturedAt) {
        return ObservationItem.create(
                UUID.randomUUID(), sessionId, null, cctvCode,
                avgHeadcount, (int) avgHeadcount, 1, density,
                CongestionLevel.NORMAL, capturedAt - 5_000, capturedAt, capturedAt, null, 1L);
    }

    // === buildZoneDensities ===

    @Test
    @DisplayName("CCTV가 매핑된 그리드 셀의 소속 구역별로 평균 밀집도를 CongestionConfig 기준 백분율로 환산한다")
    void buildZoneDensities_groupsByUserZoneAndNormalizesAgainstThreshold() {
        TrainingSession session = sessionOf(Instant.now(), Instant.now().plusSeconds(300));
        Cctv cctvA = cctv("CCTV_A");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId))
                .willReturn(List.of(cctvA));

        FloorGridCell cellInZoneA = cellInZone("A구역");
        given(cctvGridCellRepository.findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctvA.getId()))
                .willReturn(List.of(CctvGridCell.create(cctvA, cellInZoneA)));

        given(observationRepository.findAllBySessionIdAndCctvCode(sessionId.toString(), "CCTV_A", 5_000))
                .willReturn(List.of(observation("CCTV_A", 2.0, 2.5, 1_000L)));

        CongestionConfig config = mock(CongestionConfig.class);
        given(config.getVeryCrowdedFrom()).willReturn(5.0);
        given(congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID)).willReturn(Optional.of(config));

        List<ZoneDensityPoint> result = chartService.buildZoneDensities(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getZoneName()).isEqualTo("A구역");
        // density=2.5, veryCrowdedFrom=5.0 -> 50.0%
        assertThat(result.get(0).getAvgDensityPercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("구역이 지정되지 않은 셀만 감시하는 CCTV는 결과에서 제외된다")
    void buildZoneDensities_cctvWithoutZonedCell_isExcluded() {
        TrainingSession session = sessionOf(Instant.now(), Instant.now().plusSeconds(300));
        Cctv cctvNoZone = cctv("CCTV_NO_ZONE");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId))
                .willReturn(List.of(cctvNoZone));
        given(cctvGridCellRepository
                .findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctvNoZone.getId()))
                .willReturn(List.of(CctvGridCell.create(cctvNoZone, cellInZone(null))));

        List<ZoneDensityPoint> result = chartService.buildZoneDensities(session);

        assertThat(result).isEmpty();
    }

    // === buildCumulativeEvacuation ===

    @Test
    @DisplayName("건물 전체 탐지 인원이 줄어들수록 누적 대피 인원 추정치가 늘어나고 절대 감소하지 않는다")
    void buildCumulativeEvacuation_estimatesFromDecliningHeadcount() {
        Instant startedAt = Instant.ofEpochSecond(0);
        Instant endedAt = startedAt.plusSeconds(60);
        TrainingSession session = sessionOf(startedAt, endedAt);
        Cctv cctvA = cctv("CCTV_A");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId))
                .willReturn(List.of(cctvA));

        // t=0: 10명 탐지, t=60s: 0명 탐지 -> 참여인원 10명 기준 0명->10명으로 누적 증가해야 한다
        given(observationRepository.findAllBySessionIdAndCctvCode(sessionId.toString(), "CCTV_A", 5_000))
                .willReturn(List.of(
                        observation("CCTV_A", 10.0, 1.0, 0L),
                        observation("CCTV_A", 0.0, 0.0, 60_000L)));

        List<CumulativeEvacuationPoint> points = chartService.buildCumulativeEvacuation(session, 10);

        assertThat(points).isNotEmpty();
        assertThat(points.get(0).getCumulativeCount()).isZero();
        assertThat(points.get(points.size() - 1).getCumulativeCount()).isEqualTo(10);
        // 단조 비감소인지 확인
        for (int i = 1; i < points.size(); i++) {
            assertThat(points.get(i).getCumulativeCount()).isGreaterThanOrEqualTo(points.get(i - 1).getCumulativeCount());
        }
    }

    @Test
    @DisplayName("건물에 관측 데이터가 전혀 없으면 빈 목록을 반환한다")
    void buildCumulativeEvacuation_noObservations_returnsEmpty() {
        TrainingSession session = sessionOf(Instant.now(), Instant.now().plusSeconds(60));
        Cctv cctvA = cctv("CCTV_A");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId))
                .willReturn(List.of(cctvA));
        given(observationRepository.findAllBySessionIdAndCctvCode(sessionId.toString(), "CCTV_A", 5_000))
                .willReturn(List.of());

        assertThat(chartService.buildCumulativeEvacuation(session, 10)).isEmpty();
    }

    // === buildRecentEvacuationTimes ===

    @Test
    @DisplayName("같은 건물의 과거 리포트(오래된 순)에 이번 리포트 값을 마지막으로 붙인다")
    void buildRecentEvacuationTimes_appendsCurrentAfterHistoryOldestFirst() {
        TrainingReport older = mock(TrainingReport.class);
        given(older.getAvgEvacuationSec()).willReturn(200);
        TrainingReport newer = mock(TrainingReport.class);
        given(newer.getAvgEvacuationSec()).willReturn(180);
        // repository는 최신순(newer, older)으로 반환한다고 가정
        given(trainingReportRepository.findByTrainingSession_Scenario_Building_IdOrderByCreatedAtDesc(
                buildingId, PageRequest.of(0, 4)))
                .willReturn(List.of(newer, older));

        List<RecentEvacuationPoint> points = chartService.buildRecentEvacuationTimes(buildingId, 150);

        assertThat(points).hasSize(3);
        assertThat(points.get(0).getEvacuationSec()).isEqualTo(200); // 가장 오래된 것부터
        assertThat(points.get(1).getEvacuationSec()).isEqualTo(180);
        assertThat(points.get(2).getEvacuationSec()).isEqualTo(150); // 이번 리포트가 마지막
        assertThat(points.get(0).getOrdinal()).isEqualTo(1);
        assertThat(points.get(2).getOrdinal()).isEqualTo(3);
    }
}
