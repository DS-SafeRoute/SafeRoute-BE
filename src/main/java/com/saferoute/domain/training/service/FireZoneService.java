package com.saferoute.domain.training.service;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.training.dto.CreateFireZoneRequest;
import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.GridErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FireZoneService {

    private final FireZoneRepository fireZoneRepository;
    private final TrainingScenarioRepository scenarioRepository;
    private final FloorGridCellRepository gridCellRepository;
    private final SchoolContextService schoolContextService;

    //시나리오 설정 단계에서 최초 발화점을 지정
    @Transactional
    public FireZoneResponse designateOrigin(UUID scenarioId, CreateFireZoneRequest request, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        TrainingScenario scenario = scenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND));

        FloorGridCell cell = gridCellRepository.findById(request.gridCellId())
                .orElseThrow(() -> new ApiException(GridErrorCode.GRID_CELL_NOT_FOUND));

        if (!cell.getFloor().getBuilding().getId().equals(scenario.getBuildingId())) {
            throw new ApiException(GridErrorCode.GRID_CELL_FLOOR_MISMATCH);
        }

        cell.markFired();
        FireZone origin = FireZone.createOrigin(scenario, cell.getFloor(), cell);
        fireZoneRepository.save(origin);

        return FireZoneResponse.from(origin);
    }

    // 관리자가 수동 지정한 최초 발화점 목록 조회 (확산 시뮬레이션으로 생성된 FireZone은 제외)
    @Transactional(readOnly = true)
    public List<FireZoneResponse> getFireOrigins(UUID scenarioId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        if (scenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, schoolName).isEmpty()) {
            throw new ApiException(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND);
        }

        return fireZoneRepository.findByScenario_IdAndIsManualAddTrue(scenarioId).stream()
                .map(FireZoneResponse::from)
                .toList();
    }
}
