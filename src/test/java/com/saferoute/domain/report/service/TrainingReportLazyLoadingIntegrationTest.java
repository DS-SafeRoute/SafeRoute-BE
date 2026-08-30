package com.saferoute.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.report.dto.ReportResponse;
import com.saferoute.domain.report.entity.CumulativeEvacuationPoint;
import com.saferoute.domain.report.entity.Grade;
import com.saferoute.domain.report.entity.RecommendationPoint;
import com.saferoute.domain.report.entity.RecommendationPriority;
import com.saferoute.domain.report.entity.RecentEvacuationPoint;
import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.report.entity.TrainingReportCharts;
import com.saferoute.domain.report.entity.ZoneDensityPoint;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// open-in-view: false + getReport()/generatePdf()에 @Transactional이 없는 상태에서,
// TrainingReport의 @ElementCollection(차트 3종 + 권고사항, 전부 기본 LAZY)에 접근할 때
// LazyInitializationException이 나지 않는지 실제 스프링 컨텍스트로 검증한다.
// 이 테스트는 일부러 @Transactional을 붙이지 않는다 - 테스트 트랜잭션이 세션을 계속 열어두면
// 운영 환경(트랜잭션 없이 컨트롤러까지 도달)과 다른 조건이 되어 버그를 놓치게 된다.
@SpringBootTest
class TrainingReportLazyLoadingIntegrationTest {

    @Autowired
    private TrainingReportService trainingReportService;

    @Autowired
    private TrainingReportRepository trainingReportRepository;

    @Autowired
    private TrainingSessionRepository trainingSessionRepository;

    @Autowired
    private TrainingScenarioRepository trainingScenarioRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("트랜잭션 없이 getReport를 호출해도 차트/권고사항 지연 컬렉션 접근에서 예외가 나지 않는다")
    void getReport_withoutTransaction_doesNotThrowLazyInitializationException() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User admin = userRepository.save(User.create(
                "admin" + unique, "password123!", unique + "@saferoute.com", UserRole.MANAGER, "SafeRoute School"));
        Building building = buildingRepository.save(
                Building.create("테스트관", "서울특별시 안전구 1", BuildingType.CLASSROOM, "SafeRoute School"));
        TrainingScenario scenario = trainingScenarioRepository.save(TrainingScenario.create(
                "정기 훈련", 50, 300, Instant.now(), false, FireSpreadSpeed.MEDIUM, building, admin, null));

        Instant startedAt = Instant.now().minusSeconds(300);
        TrainingSession session = TrainingSession.create(TrainingStatus.RUNNING, startedAt, admin, scenario);
        session.complete(Instant.now());
        trainingSessionRepository.save(session);

        TrainingReportCharts charts = new TrainingReportCharts(
                List.of(new CumulativeEvacuationPoint(0, 0), new CumulativeEvacuationPoint(300, 50)),
                List.of(new ZoneDensityPoint("A구역", 62.0)),
                List.of(new RecentEvacuationPoint(1, 300)));
        List<RecommendationPoint> recommendations = List.of(
                new RecommendationPoint(RecommendationPriority.LOW, "제목", "설명"));

        TrainingReport report = TrainingReport.create(
                Grade.A, 92.4,
                300, 96,
                50, 48, BigDecimal.valueOf(96.0),
                1, 91,
                0.05, 94,
                charts,
                "요약 문장", recommendations,
                session);
        trainingReportRepository.saveAndFlush(report);

        // 여기서 저장에 쓰인 영속성 컨텍스트/트랜잭션은 이미 끝났다 (saveAndFlush 호출도 자체 트랜잭션).
        // 이 시점 이후 getReport()가 스스로 트랜잭션을 열지 않으면 아래에서 지연 컬렉션 접근 시 터진다.
        ReportResponse response = trainingReportService.getReport(report.getShortId(), admin.getEmail());

        assertThat(response.getCharts().cumulativeEvacuation()).hasSize(2);
        assertThat(response.getCharts().zoneDensities()).hasSize(1);
        assertThat(response.getCharts().recentEvacuationTimes()).hasSize(1);
        assertThat(response.getRecommendations()).hasSize(1);

        byte[] pdf = trainingReportService.generatePdf(report.getShortId(), admin.getEmail());
        assertThat(pdf).isNotEmpty();
    }
}
