package com.saferoute.domain.training.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateScenarioEvacuationSetupRequest(
        @NotNull UUID fireOriginGridCellId,
        @NotNull UUID startNodeId
) {
}
