package com.saferoute.domain.building.dto.request;

import com.saferoute.domain.building.entity.BuildingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 건물 등록 요청 (층수는 Floor 등록에 따라 자동 반영되므로 요청에서 받지 않는다)
public record CreateBuildingRequest(
        @NotBlank @Size(min = 2, max = 20) String name,
        @NotBlank @Size(min = 8, max = 100) String address,
        @NotNull BuildingType buildingType
) {}