package com.saferoute.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import java.util.Collections;
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
