package com.saferoute.domain.floor.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Positive;

public record UploadFloorRequest(
    @NotNull Integer floorNum,
    @Positive @NotNull Double realWidthCm,
    @Positive @NotNull Double realHeightCm,
    @NotNull MultipartFile file
) {

    private static final double CENTIMETERS_PER_METER = 100.0;

    public double realWidthMeter() {
        return realWidthCm / CENTIMETERS_PER_METER;
    }

    public double realHeightMeter() {
        return realHeightCm / CENTIMETERS_PER_METER;
    }
}
