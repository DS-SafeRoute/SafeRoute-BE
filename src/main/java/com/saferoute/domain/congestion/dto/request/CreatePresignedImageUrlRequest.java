package com.saferoute.domain.congestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record CreatePresignedImageUrlRequest(
        @NotNull UUID requestId,
        @NotNull UUID trainingSessionId,
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "cctvCode contains invalid characters")
        String cctvCode,
        @NotNull CongestionImageType imageType,
        @NotNull UUID referenceId,
        @NotNull @PositiveOrZero Long capturedAt,
        @NotBlank
        @Pattern(regexp = "image/jpeg", message = "contentType must be image/jpeg")
        String contentType
) {
}
