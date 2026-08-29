package com.saferoute.domain.training.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateFireZoneRequest(
        @NotNull UUID gridCellId
) {
}
