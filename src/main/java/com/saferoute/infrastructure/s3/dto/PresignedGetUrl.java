package com.saferoute.infrastructure.s3.dto;

import java.time.Instant;

public record PresignedGetUrl(
        String viewUrl,
        Instant expiresAt
) {
}
