package com.saferoute.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
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

    private BuildingService buildingService;

    @BeforeEach
    void setUp() {
        buildingService = new BuildingService(buildingRepository, userRepository);
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
                .isInstanceOf(ApiException.class);
    }

    private User manager() {
        return User.create(
                "manager", "encoded-password", EMAIL, UserRole.MANAGER, SCHOOL_NAME);
    }
}
