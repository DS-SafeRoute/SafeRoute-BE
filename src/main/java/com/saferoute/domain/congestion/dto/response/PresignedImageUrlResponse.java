package com.saferoute.domain.congestion.dto.response;

import com.saferoute.infrastructure.s3.dto.PresignedPutUrl;

public record PresignedImageUrlResponse(
        String objectKey,
        String uploadUrl,
        long expiresAt
) {
    public static PresignedImageUrlResponse from(
            String objectKey,
            PresignedPutUrl presignedPutUrl
    ) {
        return new PresignedImageUrlResponse(
                objectKey,
                presignedPutUrl.uploadUrl(),
                presignedPutUrl.expiresAt().toEpochMilli()
        );
    }
}
