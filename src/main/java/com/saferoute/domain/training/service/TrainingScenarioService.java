package com.saferoute.domain.training.service;

import com.saferoute.domain.building.Building;
import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.dto.UpdateScenarioRequest;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingScenarioService {

    private final TrainingScenarioRepository scenarioRepository;
    private final EntityManager entityManager;

    // 목록 조회
    public List<ScenarioResponse> getScenarios() {
        return scenarioRepository.findAll().stream()
                .map(ScenarioResponse::from)
                .collect(Collectors.toList());
    }

    // 단건 조회
    public ScenarioResponse getScenario(UUID id) {
        return ScenarioResponse.from(findById(id));
    }

    // 생성
    @Transactional
    public ScenarioResponse createScenario(CreateScenarioRequest request) {
        // TODO: BuildingRepository, UserRepository 구현 후 findById()로 교체
        // 현재는 Repository 미구현으로 getReference() 임시 사용 (DB 조회 없이 FK만 세팅)
        Building building = entityManager.getReference(
                Building.class, request.getBuildingId());
        User admin = entityManager.getReference(
                User.class, request.getAdminId());

        TrainingScenario scenario = TrainingScenario.create(
                request.getName(),
                request.getExpectedParticipants(),
                request.getScheduledAt(),
                request.getIsTemplate(),
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
                request.getIsTemplate()
        );
        return ScenarioResponse.from(scenario);
    }

    // 삭제
    @Transactional
    public void deleteScenario(UUID id) {
        TrainingScenario scenario = findById(id);
        scenarioRepository.delete(scenario);
    }

    private TrainingScenario findById(UUID id) {
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("시나리오를 찾을 수 없습니다."));
    }
}
