package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ConfigureCctvGridCellsRequest(
        @NotEmpty List<@NotNull UUID> gridCellIds
) {
}
