package com.saferoute.domain.floor.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateFloorRequest(
        // 도면 이미지 없이 층만 먼저 등록 가능
        @NotNull Integer floorNum
) {}