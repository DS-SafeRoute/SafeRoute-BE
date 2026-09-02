package com.saferoute.domain.training.service;

import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 최초 발화점 설정은 ScenarioEvacuationSetupService(POST .../evacuation-setup)가 담당한다.
// 이 서비스는 훈련/시나리오 화면에서 FireZone을 조회하는 역할만 남아 있다.
@Service
@RequiredArgsConstructor
public class FireZoneService {

    private final FireZoneRepository fireZoneRepository;
    private final TrainingScenarioRepository scenarioRepository;
    private final SchoolContextService schoolContextService;

    // 관리자가 수동 지정한 최초 발화점 목록 조회 (확산 시뮬레이션으로 생성된 FireZone은 제외)
    @Transactional(readOnly = true)
    public List<FireZoneResponse> getFireOrigins(UUID scenarioId, String email) {
        validateScenarioForSchool(scenarioId, email);
        return fireZoneRepository.findByScenario_IdAndIsManualAddTrue(scenarioId).stream()
                .map(FireZoneResponse::from)
                .toList();
    }

    // 시나리오의 전체 FireZone(수동 발화점 + 확산으로 옮겨붙은 셀) 조회 - 세대 오름차순 정렬
    @Transactional(readOnly = true)
    public List<FireZoneResponse> getFireZones(UUID scenarioId, String email) {
        validateScenarioForSchool(scenarioId, email);
        return fireZoneRepository.findByScenario_IdOrderBySpreadGenerationAscAddedAtAsc(scenarioId).stream()
                .map(FireZoneResponse::from)
                .toList();
    }

    private void validateScenarioForSchool(UUID scenarioId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        if (scenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, schoolName).isEmpty()) {
            throw new ApiException(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND);
        }
    }
}
