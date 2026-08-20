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
import com.saferoute.global.api.error.TrainingErrorCode;
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

    // 목록 조회 (deletable = 연결된 훈련 세션이 하나도 없는 시나리오인지 여부)
    public List<ScenarioResponse> getScenarios() {
        List<TrainingScenario> scenarios = scenarioRepository.findAll();
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
    public ScenarioResponse getScenario(UUID id) {
        return ScenarioResponse.from(findById(id));
    }

    // 생성
    @Transactional
    public ScenarioResponse createScenario(CreateScenarioRequest request) {
        Building building = buildingRepository.findById(request.getBuildingId())
                .orElseThrow(() -> new RuntimeException("건물을 찾을 수 없습니다."));
        User admin = userRepository.findById(request.getAdminId())
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

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
    public ScenarioResponse updateScenario(UUID id, UpdateScenarioRequest request) {
        TrainingScenario scenario = findById(id);
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
    public void deleteScenario(UUID id) {
        TrainingScenario scenario = findById(id);
        if (trainingSessionRepository.existsByScenario_Id(id)) {
            throw new ApiException(TrainingErrorCode.SCENARIO_DELETE_NOT_ALLOWED);
        }
        scenarioRepository.delete(scenario);
    }

    private TrainingScenario findById(UUID id) {
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("시나리오를 찾을 수 없습니다."));
    }
}
