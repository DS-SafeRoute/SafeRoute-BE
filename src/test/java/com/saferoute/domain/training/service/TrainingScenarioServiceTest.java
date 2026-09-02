package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrainingScenarioServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private TrainingScenarioService trainingScenarioService;

    @Mock
    private TrainingScenarioRepository scenarioRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private TrainingReportRepository trainingReportRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolContextService schoolContextService;

    private TrainingScenario scenarioWithId(UUID id) {
        TrainingScenario scenario = TrainingScenario.create(
                "테스트 시나리오",
                10,
                300,
                Instant.now(),
                false,
                FireSpreadSpeed.MEDIUM,
                mock(Building.class),
                mock(User.class),
                null);
        ReflectionTestUtils.setField(scenario, "id", id);
        return scenario;
    }

    @Test
    @DisplayName("startNodeId와 목표 대피 시간 없이 시나리오를 생성한다")
    void createScenario_withoutStartNodeAndTarget_succeeds() {
        UUID buildingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Building building = mock(Building.class);
        User admin = mock(User.class);
        CreateScenarioRequest request = new CreateScenarioRequest(
                "정기 훈련", buildingId, 52, null, Instant.now(), false, adminId, FireSpreadSpeed.MEDIUM);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(buildingRepository.findByIdAndSchoolNameForUpdate(buildingId, SCHOOL_NAME))
                .willReturn(Optional.of(building));
        given(userRepository.findByIdAndSchoolName(adminId, SCHOOL_NAME)).willReturn(Optional.of(admin));
        given(scenarioRepository.save(org.mockito.ArgumentMatchers.any(TrainingScenario.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ScenarioResponse response = trainingScenarioService.createScenario(request, EMAIL);

        assertThat(response.getStartNodeId()).isNull();
        assertThat(response.getTargetEvacuationSec()).isNull();
    }

    // === getScenarios (deletable) ===

    @Test
    @DisplayName("연결된 훈련 세션이 없는 시나리오는 deletable=true로 응답한다")
    void getScenarios_scenarioWithoutSession_isDeletable() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findAllByBuilding_SchoolNameOrderByCreatedAtDesc(SCHOOL_NAME))
                .willReturn(List.of(scenario));
        given(trainingSessionRepository.findScenarioIdsWithAnySession(List.of(scenarioId)))
                .willReturn(Set.of());
        given(trainingReportRepository.findReportIdsByScenarioIds(List.of(scenarioId)))
                .willReturn(List.of());

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios(EMAIL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDeletable()).isTrue();
    }

    @Test
    @DisplayName("연결된 훈련 세션이 하나라도 있는 시나리오는 deletable=false로 응답한다")
    void getScenarios_scenarioWithSession_isNotDeletable() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findAllByBuilding_SchoolNameOrderByCreatedAtDesc(SCHOOL_NAME))
                .willReturn(List.of(scenario));
        given(trainingSessionRepository.findScenarioIdsWithAnySession(List.of(scenarioId)))
                .willReturn(Set.of(scenarioId));
        given(trainingReportRepository.findReportIdsByScenarioIds(List.of(scenarioId)))
                .willReturn(List.of());

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios(EMAIL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDeletable()).isFalse();
    }

    @Test
    @DisplayName("리포트가 생성된 시나리오는 목록에서 reportId를 함께 응답한다")
    void getScenarios_scenarioWithReport_returnsReportId() {
        UUID scenarioId = UUID.randomUUID();
        String reportId = "abc123XYZ0";
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findAllByBuilding_SchoolNameOrderByCreatedAtDesc(SCHOOL_NAME))
                .willReturn(List.of(scenario));
        given(trainingSessionRepository.findScenarioIdsWithAnySession(List.of(scenarioId)))
                .willReturn(Set.of(scenarioId));
        given(trainingReportRepository.findReportIdsByScenarioIds(List.of(scenarioId)))
                .willReturn(List.of(scenarioReportId(scenarioId, reportId)));

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios(EMAIL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getReportId()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("리포트가 없는 시나리오는 목록에서 reportId가 null이다")
    void getScenarios_scenarioWithoutReport_returnsNullReportId() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findAllByBuilding_SchoolNameOrderByCreatedAtDesc(SCHOOL_NAME))
                .willReturn(List.of(scenario));
        given(trainingSessionRepository.findScenarioIdsWithAnySession(List.of(scenarioId)))
                .willReturn(Set.of());
        given(trainingReportRepository.findReportIdsByScenarioIds(List.of(scenarioId)))
                .willReturn(List.of());

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios(EMAIL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getReportId()).isNull();
    }

    private TrainingReportRepository.ScenarioReportId scenarioReportId(UUID scenarioId, String reportId) {
        return new TrainingReportRepository.ScenarioReportId() {
            @Override
            public UUID getScenarioId() {
                return scenarioId;
            }

            @Override
            public String getReportId() {
                return reportId;
            }
        };
    }

    @Test
    @DisplayName("시나리오가 하나도 없으면 세션/리포트 조회 없이 빈 목록을 반환한다")
    void getScenarios_noScenarios_returnsEmptyListWithoutQueryingSessions() {
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findAllByBuilding_SchoolNameOrderByCreatedAtDesc(SCHOOL_NAME))
                .willReturn(List.of());

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios(EMAIL);

        assertThat(responses).isEmpty();
        verify(trainingSessionRepository, never()).findScenarioIdsWithAnySession(org.mockito.ArgumentMatchers.any());
        verify(trainingReportRepository, never()).findReportIdsByScenarioIds(org.mockito.ArgumentMatchers.any());
    }

    // === deleteScenario ===

    @Test
    @DisplayName("연결된 훈련 세션이 없으면 시나리오를 삭제할 수 있다")
    void deleteScenario_withoutSession_deletesScenario() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(scenario));
        given(trainingSessionRepository.existsByScenario_Id(scenarioId)).willReturn(false);

        trainingScenarioService.deleteScenario(scenarioId, EMAIL);

        verify(scenarioRepository).delete(scenario);
    }

    @Test
    @DisplayName("연결된 훈련 세션이 하나라도 있으면 시나리오 삭제가 거부된다")
    void deleteScenario_withSession_throwsException() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(scenario));
        given(trainingSessionRepository.existsByScenario_Id(scenarioId)).willReturn(true);

        assertThatThrownBy(() -> trainingScenarioService.deleteScenario(scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.SCENARIO_DELETE_NOT_ALLOWED);
        verify(scenarioRepository, never()).delete(scenario);
    }
}
