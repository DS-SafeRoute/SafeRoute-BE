package com.saferoute.domain.congestion.dto.response;

import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import java.time.Instant;

// 관리자 화면이 S3 object key 대신 이 URL로 이미지를 직접 렌더링한다. URL은 만료되므로
// 화면에 값을 오래 들고 있지 말고, 열람 시점에 다시 요청해서 새 URL을 받아야 한다.
public record CongestionImageUrlResponse(
        String imageUrl,
        Instant expiresAt
) {

    public static CongestionImageUrlResponse from(PresignedGetUrl presignedGetUrl) {
        return new CongestionImageUrlResponse(presignedGetUrl.viewUrl(), presignedGetUrl.expiresAt());
    }
}
