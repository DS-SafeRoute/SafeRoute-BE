package com.saferoute.domain.building.dto;

import com.saferoute.domain.building.entity.BuildingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 건물 등록 요청
public record CreateBuildingRequest(
        @NotBlank @Size(min = 2, max = 20) String name,
        @NotBlank @Size(min = 8, max = 100) String address,
        @NotNull Integer totalFloors,
        @NotNull BuildingType buildingType
) {}