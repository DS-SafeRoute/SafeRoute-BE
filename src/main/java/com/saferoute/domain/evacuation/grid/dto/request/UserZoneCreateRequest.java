package com.saferoute.domain.evacuation.grid.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record UserZoneCreateRequest(
        @NotBlank String name,
        @NotEmpty
        List<UUID> cellIds
) {
}
