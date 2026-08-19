package com.saferoute.domain.congestion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ConnectEventImageRequest(
        @NotBlank String eventImageKey,
        @NotNull @PositiveOrZero Long uploadedAt
) {
}
