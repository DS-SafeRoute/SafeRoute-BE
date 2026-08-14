package com.saferoute.domain.floor.dto.request;

import jakarta.validation.constraints.NotNull;

// 층 정보 수정 요청
public record UpdateFloorRequest(
        @NotNull Integer floorNum
) {}
