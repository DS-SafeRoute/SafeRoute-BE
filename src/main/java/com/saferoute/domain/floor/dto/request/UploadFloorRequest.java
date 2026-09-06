package com.saferoute.domain.floor.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Positive;

public record UploadFloorRequest(
    @NotNull Integer floorNum,
    @Positive @NotNull Double realWidth,
    @Positive @NotNull Double realHeight,
    @NotNull MultipartFile file
) {

    private static final double CENTIMETERS_PER_METER = 100.0;

    public double realWidthMeter() {
        return realWidth / CENTIMETERS_PER_METER;
    }

    public double realHeightMeter() {
        return realHeight / CENTIMETERS_PER_METER;
    }
}
