package com.saferoute.domain.building.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;

import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final UserRepository userRepository;
    private final FloorRepository floorRepository;
    private final TrainingScenarioRepository trainingScenarioRepository;

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

    @Transactional
    public void deleteBuilding(UUID buildingId, String email) {
        Building building = findBuildingByIdAndEmailForUpdate(buildingId, email);

        if (trainingScenarioRepository.existsByBuilding_Id(buildingId)) {
            throw new ApiException(BuildingErrorCode.BUILDING_HAS_TRAINING_HISTORY);
        }

        // Floor 하위 도면 데이터는 DB ON DELETE CASCADE로 함께 정리된다.
        // 건물을 삭제하기 전에 Floor FK를 먼저 제거해야 한다.
        floorRepository.deleteAllByBuilding_Id(buildingId);
        floorRepository.flush();
        buildingRepository.delete(building);
    }

    private Building findBuildingByIdAndEmail(UUID buildingId, String email) {
        String schoolName = findUserByEmail(email).getSchoolName();
        return buildingRepository.findByIdAndSchoolName(buildingId, schoolName)
                .orElseThrow(() -> new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND));
    }

    private Building findBuildingByIdAndEmailForUpdate(UUID buildingId, String email) {
        String schoolName = findUserByEmail(email).getSchoolName();
        return buildingRepository.findByIdAndSchoolNameForUpdate(buildingId, schoolName)
                .orElseThrow(() -> new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));
    }
}
