package com.saferoute.domain.floor.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.saferoute.domain.analysis.service.FloorAnalysisService;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.service.S3Service;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FloorServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private FloorService floorService;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorAnalysisService floorAnalysisService;

    @Mock
    private S3Service s3Service;

    @Mock
    private SchoolContextService schoolContextService;

    @Test
    void hidesFloorsOfBuildingFromAnotherSchool() {
        UUID buildingId = UUID.randomUUID();
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(buildingRepository.findByIdAndSchoolName(buildingId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> floorService.getFloors(buildingId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(BuildingErrorCode.BUILDING_NOT_FOUND);
    }

    @Test
    void hidesFloorAnalysisTargetFromAnotherSchool() {
        UUID floorId = UUID.randomUUID();
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(floorRepository.findByIdAndBuilding_SchoolName(floorId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> floorService.requestAnalysis(floorId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(FloorErrorCode.FLOOR_NOT_FOUND);
    }
}
