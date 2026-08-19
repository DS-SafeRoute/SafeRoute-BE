package com.saferoute.infrastructure.s3.dto;

import java.time.Instant;

public record PresignedPutUrl(
        String uploadUrl,
        Instant expiresAt
) {
}
