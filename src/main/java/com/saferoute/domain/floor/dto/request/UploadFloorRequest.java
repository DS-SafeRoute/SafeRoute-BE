package com.saferoute.domain.floor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record UploadFloorRequest(
    @NotBlank String buildingName,
    @NotNull Integer floorNum,
    @NotNull Double realWidth,
    @NotNull Double realHeight,
    @NotNull MultipartFile file
) {

}
