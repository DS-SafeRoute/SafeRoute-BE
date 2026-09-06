package com.saferoute.domain.evacuation.grid.dto.request;

import jakarta.validation.constraints.Positive;

public record CreateOrUpdateFloorGridRequest(
        @Positive double cellSizeCm
) {

    private static final double CENTIMETERS_PER_METER = 100.0;

    public double cellSizeMeter() {
        return cellSizeCm / CENTIMETERS_PER_METER;
    }
}
