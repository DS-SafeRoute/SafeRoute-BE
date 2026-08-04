package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateIoTLightRequest(
        @NotBlank String name,
        @NotNull Double x,
        @NotNull Double y
) {}
