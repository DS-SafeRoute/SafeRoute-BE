package com.saferoute.domain.building.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;

import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final UserRepository userRepository;

    @Transactional
    public BuildingResponse createBuilding(CreateBuildingRequest request, String email) {
        String schoolName = findUserByEmail(email).getSchoolName();
        Building building = Building.create(
                request.name(),
                request.address(),
                request.buildingType(),
                schoolName
        );
        return BuildingResponse.from(buildingRepository.save(building));
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> getBuildings(String email) {
        String schoolName = findUserByEmail(email).getSchoolName();
        return buildingRepository.findAllBySchoolNameOrderByCreatedAtDesc(schoolName).stream()
                .map(BuildingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BuildingResponse getBuilding(UUID buildingId, String email) {
        return BuildingResponse.from(findBuildingByIdAndEmail(buildingId, email));
    }

    @Transactional
    public BuildingResponse updateBuilding(UUID buildingId, UpdateBuildingRequest request, String email) {
        Building building = findBuildingByIdAndEmail(buildingId, email);
        building.update(request.name(), request.address(), request.buildingType());
        return BuildingResponse.from(building);
    }

    @Transactional
    public void deactivateBuilding(UUID buildingId, String email) {
        findBuildingByIdAndEmail(buildingId, email).deactivate();
    }

    // TrainingScenario.building 에는 CASCADE가 없으므로, 이 건물을 참조하는 훈련 기록이
    // 하나라도 있으면 DB가 FK 위반으로 삭제를 막는다 — 그 경우 deactivateBuilding()을 쓸 것.
    @Transactional
    public void deleteBuilding(UUID buildingId, String email) {
        Building building = findBuildingByIdAndEmail(buildingId, email);
        try {
            buildingRepository.delete(building);
            buildingRepository.flush(); // 트랜잭션 커밋 전에 FK 위반을 여기서 확인
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(BuildingErrorCode.BUILDING_HAS_TRAINING_HISTORY);
        }
    }

    private Building findBuildingByIdAndEmail(UUID buildingId, String email) {
        String schoolName = findUserByEmail(email).getSchoolName();
        return buildingRepository.findByIdAndSchoolName(buildingId, schoolName)
                .orElseThrow(() -> new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));
    }
}
