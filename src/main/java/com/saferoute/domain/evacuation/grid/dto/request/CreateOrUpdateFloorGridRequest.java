package com.saferoute.domain.evacuation.grid.dto.request;

import jakarta.validation.constraints.Positive;

public record CreateOrUpdateFloorGridRequest(
        @Positive double cellSizeMeter
) {
}
