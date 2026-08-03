package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfigureGuidanceRequest(
        @NotNull UUID decisionNodeId,
        @NotNull UUID leftEdgeId,
        @NotNull UUID rightEdgeId
) {}
