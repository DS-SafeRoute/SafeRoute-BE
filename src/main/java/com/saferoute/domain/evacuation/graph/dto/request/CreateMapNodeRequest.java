package com.saferoute.domain.evacuation.graph.dto.request;

import com.saferoute.domain.evacuation.graph.entity.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMapNodeRequest(
        @NotBlank String code,
        @NotNull NodeType type,
        @NotBlank String name,
        @NotNull Double x,
        @NotNull Double y,
        boolean isExitTarget
) {}
