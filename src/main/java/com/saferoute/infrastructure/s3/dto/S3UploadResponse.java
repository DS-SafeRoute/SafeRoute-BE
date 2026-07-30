package com.saferoute.infrastructure.s3.dto;

public record S3UploadResponse(
        String bucket,
        String key,
        String s3Uri,
        long size,
        String contentType
) {
}
