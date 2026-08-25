package com.saferoute.domain.training.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.dto.UpdateScenarioRequest;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingScenarioService {

    private final TrainingScenarioRepository scenarioRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final BuildingRepository buildingRepository;
    private final UserRepository userRepository;
    private final SchoolContextService schoolContextService;

    // 목록 조회 (deletable = 연결된 훈련 세션이 하나도 없는 시나리오인지 여부)
    public List<ScenarioResponse> getScenarios(String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        List<TrainingScenario> scenarios =
                scenarioRepository.findAllByBuilding_SchoolNameOrderByCreatedAtDesc(schoolName);
        if (scenarios.isEmpty()) {
            return List.of();
        }

        List<UUID> scenarioIds = scenarios.stream().map(TrainingScenario::getId).toList();
        Set<UUID> scenarioIdsWithSession = trainingSessionRepository.findScenarioIdsWithAnySession(scenarioIds);

        return scenarios.stream()
                .map(scenario -> ScenarioResponse.from(scenario, !scenarioIdsWithSession.contains(scenario.getId())))
                .toList();
    }

    // 단건 조회
    public ScenarioResponse getScenario(UUID id, String email) {
        return ScenarioResponse.from(findByIdAndEmail(id, email));
    }

    // 생성
    @Transactional
    public ScenarioResponse createScenario(CreateScenarioRequest request, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        Building building = buildingRepository.findByIdAndSchoolName(request.getBuildingId(), schoolName)
                .orElseThrow(() -> new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND));
        User admin = userRepository.findByIdAndSchoolName(request.getAdminId(), schoolName)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

        TrainingScenario scenario = TrainingScenario.create(
                request.getName(),
                request.getExpectedParticipants(),
                request.getScheduledAt(),
                request.getIsTemplate(),
                request.getFireSpreadSpeed(),
                building,
                admin
        );
        return ScenarioResponse.from(scenarioRepository.save(scenario));
    }

    // 수정
    @Transactional
    public ScenarioResponse updateScenario(UUID id, UpdateScenarioRequest request, String email) {
        TrainingScenario scenario = findByIdAndEmail(id, email);
        scenario.update(
                request.getName(),
                request.getExpectedParticipants(),
                request.getScheduledAt(),
                request.getIsTemplate(),
                request.getFireSpreadSpeed()
        );
        return ScenarioResponse.from(scenario);
    }

    // 삭제 (훈련 세션이 하나라도 연결된 시나리오는 삭제 불가 - 과거 훈련 기록 보존을 위해)
    @Transactional
    public void deleteScenario(UUID id, String email) {
        TrainingScenario scenario = findByIdAndEmail(id, email);
        if (trainingSessionRepository.existsByScenario_Id(id)) {
            throw new ApiException(TrainingErrorCode.SCENARIO_DELETE_NOT_ALLOWED);
        }
        scenarioRepository.delete(scenario);
    }

    private TrainingScenario findByIdAndEmail(UUID id, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        return scenarioRepository.findByIdAndBuilding_SchoolName(id, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND));
    }
}
