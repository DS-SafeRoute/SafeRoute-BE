package com.saferoute.domain.evacuation.graph.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMapEdgeRequest(
        @NotNull UUID fromNodeId,
        @NotNull UUID toNodeId,
        @NotNull Double distance,
        @NotNull Boolean bidirectional
) {}
