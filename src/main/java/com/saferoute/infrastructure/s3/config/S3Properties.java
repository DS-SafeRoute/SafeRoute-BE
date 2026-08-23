package com.saferoute.infrastructure.s3.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        @NotBlank String bucket,
        @NotNull Duration presignedUrlExpiration,
        @NotNull Duration presignedGetUrlExpiration
) {
}