package com.saferoute.domain.evacuation.graph.dto.response;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import java.util.UUID;

public record MapNodeResponse(
        UUID id,
        String code,
        NodeType type,
        String name,
        double x,
        double y,
        boolean isExitTarget
) {
    public static MapNodeResponse from(MapNode node) {
        return new MapNodeResponse(
                node.getId(),
                node.getCode(),
                node.getType(),
                node.getName(),
                node.getX(),
                node.getY(),
                node.isExitTarget()
        );
    }
}
