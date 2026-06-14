package com.saferoute.domain.training.service;

import com.saferoute.domain.building.Building;
import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.dto.UpdateScenarioRequest;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.entity.User;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrainingScenarioServiceTest {

    @InjectMocks
    private TrainingScenarioService scenarioService;

    @Mock
    private TrainingScenarioRepository scenarioRepository;

    @Mock
    private EntityManager entityManager;

    private final UUID scenarioId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final Instant scheduledAt = Instant.parse("2026-07-01T09:00:00Z");

    @Test
    @DisplayName("시나리오 목록 조회 - 성공")
    void getScenarios_success() {
        // given
        Building building = mock(Building.class);
        User admin = mock(User.class);
        given(building.getId()).willReturn(buildingId);
        given(admin.getId()).willReturn(adminId);

        TrainingScenario scenario1 = TrainingScenario.create(
                "시나리오A", 30, scheduledAt, false, building, admin);
        TrainingScenario scenario2 = TrainingScenario.create(
                "시나리오B", 20, scheduledAt, true, building, admin);
        given(scenarioRepository.findAll()).willReturn(List.of(scenario1, scenario2));

        // when
        List<ScenarioResponse> result = scenarioService.getScenarios();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("시나리오A");
        assertThat(result.get(1).getName()).isEqualTo("시나리오B");
    }

    @Test
    @DisplayName("시나리오 단건 조회 - 성공")
    void getScenario_success() {
        // given
        Building building = mock(Building.class);
        User admin = mock(User.class);
        given(building.getId()).willReturn(buildingId);
        given(admin.getId()).willReturn(adminId);

        TrainingScenario scenario = TrainingScenario.create(
                "시나리오A", 30, scheduledAt, false, building, admin);
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.of(scenario));

        // when
        ScenarioResponse result = scenarioService.getScenario(scenarioId);

        // then
        assertThat(result.getName()).isEqualTo("시나리오A");
        assertThat(result.getExpectedParticipants()).isEqualTo(30);
        assertThat(result.getBuildingId()).isEqualTo(buildingId);
    }

    @Test
    @DisplayName("시나리오 단건 조회 - 없는 ID면 예외 발생")
    void getScenario_notFound() {
        // given
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scenarioService.getScenario(scenarioId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("시나리오를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("시나리오 생성 - 성공")
    void createScenario_success() {
        // given
        CreateScenarioRequest request = new CreateScenarioRequest(
                "새시나리오", buildingId, 30, scheduledAt, false, adminId);

        Building building = mock(Building.class);
        User admin = mock(User.class);
        given(building.getId()).willReturn(buildingId);
        given(admin.getId()).willReturn(adminId);

        // EntityManager.getReference() 가 mock 객체 반환하도록 설정
        given(entityManager.getReference(eq(Building.class), eq(buildingId)))
                .willReturn(building);
        given(entityManager.getReference(eq(User.class), eq(adminId)))
                .willReturn(admin);

        TrainingScenario savedScenario = TrainingScenario.create(
                "새시나리오", 30, scheduledAt, false, building, admin);
        given(scenarioRepository.save(any(TrainingScenario.class))).willReturn(savedScenario);

        // when
        ScenarioResponse result = scenarioService.createScenario(request);

        // then
        assertThat(result.getName()).isEqualTo("새시나리오");
        assertThat(result.getBuildingId()).isEqualTo(buildingId);
        verify(scenarioRepository).save(any(TrainingScenario.class));
    }

    @Test
    @DisplayName("시나리오 수정 - 성공")
    void updateScenario_success() {
        // given
        Building building = mock(Building.class);
        User admin = mock(User.class);

        TrainingScenario scenario = TrainingScenario.create(
                "기존이름", 30, scheduledAt, false, building, admin);
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.of(scenario));

        UpdateScenarioRequest request = new UpdateScenarioRequest(
                "수정된이름", null, null, null);

        // when
        ScenarioResponse result = scenarioService.updateScenario(scenarioId, request);

        // then
        assertThat(result.getName()).isEqualTo("수정된이름");
        assertThat(result.getExpectedParticipants()).isEqualTo(30); // 안 바꾼 값 유지
    }

    @Test
    @DisplayName("시나리오 삭제 - 성공")
    void deleteScenario_success() {
        // given
        Building building = mock(Building.class);
        User admin = mock(User.class);

        TrainingScenario scenario = TrainingScenario.create(
                "삭제할시나리오", 30, scheduledAt, false, building, admin);
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.of(scenario));

        // when
        scenarioService.deleteScenario(scenarioId);

        // then
        verify(scenarioRepository).delete(scenario);
    }

    @Test
    @DisplayName("시나리오 삭제 - 없는 ID면 예외 발생")
    void deleteScenario_notFound() {
        // given
        given(scenarioRepository.findById(scenarioId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scenarioService.deleteScenario(scenarioId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("시나리오를 찾을 수 없습니다.");
    }
}
