package com.saferoute.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private TrainingScenarioRepository trainingScenarioRepository;

    private BuildingService buildingService;

    @BeforeEach
    void setUp() {
        buildingService = new BuildingService(
                buildingRepository,
                userRepository,
                floorRepository,
                trainingScenarioRepository);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(manager()));
    }

    @Test
    void createsBuildingForAuthenticatedUsersSchool() {
        CreateBuildingRequest request = new CreateBuildingRequest(
                "공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM);
        given(buildingRepository.save(org.mockito.ArgumentMatchers.any(Building.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = buildingService.createBuilding(request, EMAIL);

        assertThat(response.schoolName()).isEqualTo(SCHOOL_NAME);
    }

    @Test
    void listsOnlyBuildingsFromAuthenticatedUsersSchool() {
        Building building = Building.create(
                "공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM, SCHOOL_NAME);
        given(buildingRepository.findAllBySchoolNameOrderByCreatedAtDesc(SCHOOL_NAME))
                .willReturn(List.of(building));

        var responses = buildingService.getBuildings(EMAIL);

        assertThat(responses).hasSize(1);
        verify(buildingRepository).findAllBySchoolNameOrderByCreatedAtDesc(SCHOOL_NAME);
    }

    @Test
    void hidesBuildingFromAnotherSchool() {
        UUID buildingId = UUID.randomUUID();
        given(buildingRepository.findByIdAndSchoolName(buildingId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> buildingService.getBuilding(buildingId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(BuildingErrorCode.BUILDING_NOT_FOUND);
    }

    @Test
    void deletesFloorsBeforeDeletingBuildingWhenTrainingHistoryDoesNotExist() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.create(
                "공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM, SCHOOL_NAME);
        given(buildingRepository.findByIdAndSchoolName(buildingId, SCHOOL_NAME))
                .willReturn(Optional.of(building));
        given(trainingScenarioRepository.existsByBuilding_Id(buildingId)).willReturn(false);

        buildingService.deleteBuilding(buildingId, EMAIL);

        InOrder deletionOrder = inOrder(floorRepository, buildingRepository);
        deletionOrder.verify(floorRepository).deleteAllByBuilding_Id(buildingId);
        deletionOrder.verify(floorRepository).flush();
        deletionOrder.verify(buildingRepository).delete(building);
    }

    @Test
    void rejectsBuildingDeletionWhenTrainingHistoryExists() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.create(
                "공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM, SCHOOL_NAME);
        given(buildingRepository.findByIdAndSchoolName(buildingId, SCHOOL_NAME))
                .willReturn(Optional.of(building));
        given(trainingScenarioRepository.existsByBuilding_Id(buildingId)).willReturn(true);

        assertThatThrownBy(() -> buildingService.deleteBuilding(buildingId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(BuildingErrorCode.BUILDING_HAS_TRAINING_HISTORY);

        verify(floorRepository, never()).deleteAllByBuilding_Id(buildingId);
        verify(buildingRepository, never()).delete(building);
    }

    private User manager() {
        return User.create(
                "manager", "encoded-password", EMAIL, UserRole.MANAGER, SCHOOL_NAME);
    }
}
