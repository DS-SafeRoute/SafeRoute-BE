package com.saferoute.domain.floor.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Positive;

// realWidth/realHeight는 프론트가 미터 단위로 그대로 보낸다 - 그리드 셀 크기(cellSizeMeter,
// CreateOrUpdateFloorGridRequest 참고)와 달리 센티미터 변환이 필요 없다.
public record UploadFloorRequest(
    @NotNull Integer floorNum,
    @Positive @NotNull Double realWidth,
    @Positive @NotNull Double realHeight,
    @NotNull MultipartFile file
) {
}
