package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateIoTLightRequest(
        @NotNull UUID floorId,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull Double x,
        @NotNull Double y
) {}
