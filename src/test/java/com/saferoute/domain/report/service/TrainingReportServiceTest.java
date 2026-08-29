package com.saferoute.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.ReportErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainingReportServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private TrainingReportService trainingReportService;

    @Mock
    private TrainingReportRepository trainingReportRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private SchoolContextService schoolContextService;

    @Test
    @DisplayName("shortId와 기관이 일치하는 리포트를 조회할 수 있다")
    void getReport_matchingShortIdAndSchool_returnsReport() {
        String reportId = "abc123XYZ0";
        TrainingReport report = mock(TrainingReport.class);
        given(report.getShortId()).willReturn(reportId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(trainingReportRepository
                .findByShortIdAndTrainingSession_Scenario_Building_SchoolName(reportId, SCHOOL_NAME))
                .willReturn(Optional.of(report));

        var response = trainingReportService.getReport(reportId, EMAIL);

        assertThat(response.getReportId()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("존재하지 않거나 다른 기관의 shortId로 조회하면 예외가 발생한다")
    void getReport_notFound_throwsException() {
        String reportId = "abc123XYZ0";
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(trainingReportRepository
                .findByShortIdAndTrainingSession_Scenario_Building_SchoolName(reportId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> trainingReportService.getReport(reportId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void calculatesDashboardStatsOnlyForAuthenticatedUsersSchool() {
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(trainingSessionRepository.countByScenario_Building_SchoolName(SCHOOL_NAME))
                .willReturn(3L);
        given(trainingReportRepository.getStatisticsBySchoolName(SCHOOL_NAME))
                .willReturn(Collections.singletonList(new Object[]{80.5, 120.0, 42L}));

        var response = trainingReportService.getStats(EMAIL);

        assertThat(response.getTotalSessions()).isEqualTo(3L);
        verify(trainingReportRepository).getStatisticsBySchoolName(SCHOOL_NAME);
    }
}
