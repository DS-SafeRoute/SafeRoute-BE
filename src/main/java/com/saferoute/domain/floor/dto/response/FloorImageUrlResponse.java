package com.saferoute.domain.floor.dto.response;

import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import java.time.Instant;

// 프론트가 mapImageKey 대신 이 URL로 도면 이미지를 직접 렌더링한다. URL은 만료되므로
// 화면에 값을 오래 들고 있지 말고, 만료 시점에 다시 요청해서 새 URL을 받아야 한다.
public record FloorImageUrlResponse(
        String imageUrl,
        Instant expiresAt
) {

    public static FloorImageUrlResponse from(PresignedGetUrl presignedGetUrl) {
        return new FloorImageUrlResponse(presignedGetUrl.viewUrl(), presignedGetUrl.expiresAt());
    }
}
