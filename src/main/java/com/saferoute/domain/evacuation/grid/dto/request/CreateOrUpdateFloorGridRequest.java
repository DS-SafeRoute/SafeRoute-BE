package com.saferoute.domain.evacuation.grid.dto.request;

import jakarta.validation.constraints.Positive;

public record CreateOrUpdateFloorGridRequest(
        @Positive double cellSizeMeter
) {

    private static final double CENTIMETERS_PER_METER = 100.0;

    public double cellSizeInMeters() {
        return cellSizeMeter / CENTIMETERS_PER_METER;
    }
}
