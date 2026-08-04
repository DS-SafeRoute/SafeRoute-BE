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

}
