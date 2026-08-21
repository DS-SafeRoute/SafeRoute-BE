package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
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

    @InjectMocks
    private TrainingScenarioService trainingScenarioService;

    @Mock
    private TrainingScenarioRepository scenarioRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UserRepository userRepository;

    private TrainingScenario scenarioWithId(UUID id) {
        TrainingScenario scenario = TrainingScenario.create(
                "테스트 시나리오",
                10,
                Instant.now(),
                false,
                FireSpreadSpeed.MEDIUM,
                mock(Building.class),
                mock(User.class));
        ReflectionTestUtils.setField(scenario, "id", id);
        return scenario;
    }

    // === getScenarios (deletable) ===

    @Test
    @DisplayName("연결된 훈련 세션이 없는 시나리오는 deletable=true로 응답한다")
    void getScenarios_scenarioWithoutSession_isDeletable() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(scenarioRepository.findAll()).willReturn(List.of(scenario));
        given(trainingSessionRepository.findScenarioIdsWithAnySession(List.of(scenarioId)))
                .willReturn(Set.of());

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDeletable()).isTrue();
    }

    @Test
    @DisplayName("연결된 훈련 세션이 하나라도 있는 시나리오는 deletable=false로 응답한다")
    void getScenarios_scenarioWithSession_isNotDeletable() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(scenarioRepository.findAll()).willReturn(List.of(scenario));
        given(trainingSessionRepository.findScenarioIdsWithAnySession(List.of(scenarioId)))
                .willReturn(Set.of(scenarioId));

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDeletable()).isFalse();
    }

    @Test
    @DisplayName("시나리오가 하나도 없으면 세션 조회 없이 빈 목록을 반환한다")
    void getScenarios_noScenarios_returnsEmptyListWithoutQueryingSessions() {
        given(scenarioRepository.findAll()).willReturn(List.of());

        List<ScenarioResponse> responses = trainingScenarioService.getScenarios();

        assertThat(responses).isEmpty();
        verify(trainingSessionRepository, never()).findScenarioIdsWithAnySession(org.mockito.ArgumentMatchers.any());
    }

    // === deleteScenario ===

    @Test
    @DisplayName("연결된 훈련 세션이 없으면 시나리오를 삭제할 수 있다")
    void deleteScenario_withoutSession_deletesScenario() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.of(scenario));
        given(trainingSessionRepository.existsByScenario_Id(scenarioId)).willReturn(false);

        trainingScenarioService.deleteScenario(scenarioId);

        verify(scenarioRepository).delete(scenario);
    }

    @Test
    @DisplayName("연결된 훈련 세션이 하나라도 있으면 시나리오 삭제가 거부된다")
    void deleteScenario_withSession_throwsException() {
        UUID scenarioId = UUID.randomUUID();
        TrainingScenario scenario = scenarioWithId(scenarioId);
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.of(scenario));
        given(trainingSessionRepository.existsByScenario_Id(scenarioId)).willReturn(true);

        assertThatThrownBy(() -> trainingScenarioService.deleteScenario(scenarioId))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.SCENARIO_DELETE_NOT_ALLOWED);
        verify(scenarioRepository, never()).delete(scenario);
    }
}
