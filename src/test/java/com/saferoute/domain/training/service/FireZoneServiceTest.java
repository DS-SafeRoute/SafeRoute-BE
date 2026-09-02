package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 최초 발화점 등록(designateOrigin)은 ScenarioEvacuationSetupServiceTest로 이전되었다.
// 이 테스트는 남은 조회 전용 메서드만 다룬다.
@ExtendWith(MockitoExtension.class)
class FireZoneServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private FireZoneService fireZoneService;

    @Mock
    private FireZoneRepository fireZoneRepository;
    @Mock
    private TrainingScenarioRepository scenarioRepository;
    @Mock
    private SchoolContextService schoolContextService;

    private final UUID scenarioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
    }

    @Test
    @DisplayName("수동으로 지정한 최초 발화점만 조회한다")
    void getFireOrigins_returnsManualOriginsOnly() {
        given(scenarioRepository.findByIdAndAdmin_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(mock(TrainingScenario.class)));
        given(fireZoneRepository.findByScenario_IdAndIsManualAddTrue(scenarioId))
                .willReturn(List.of(mock(FireZone.class)));

        List<FireZoneResponse> response = fireZoneService.getFireOrigins(scenarioId, EMAIL);

        assertThat(response).hasSize(1);
    }

    @Test
    @DisplayName("다른 학교 소속 시나리오의 발화점은 조회할 수 없다")
    void getFireOrigins_otherSchool_throws() {
        given(scenarioRepository.findByIdAndAdmin_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> fireZoneService.getFireOrigins(scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND);
    }

    @Test
    @DisplayName("시나리오의 전체 FireZone을 세대순으로 조회한다")
    void getFireZones_returnsAllOrderedBySpreadGeneration() {
        given(scenarioRepository.findByIdAndAdmin_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(mock(TrainingScenario.class)));
        given(fireZoneRepository.findByScenario_IdOrderBySpreadGenerationAscAddedAtAsc(scenarioId))
                .willReturn(List.of(mock(FireZone.class), mock(FireZone.class)));

        List<FireZoneResponse> response = fireZoneService.getFireZones(scenarioId, EMAIL);

        assertThat(response).hasSize(2);
    }

    @Test
    @DisplayName("다른 학교 소속 시나리오의 화재구역은 조회할 수 없다")
    void getFireZones_otherSchool_throws() {
        given(scenarioRepository.findByIdAndAdmin_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> fireZoneService.getFireZones(scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND);
    }
}
