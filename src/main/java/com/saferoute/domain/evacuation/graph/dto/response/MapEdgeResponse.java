package com.saferoute.domain.evacuation.graph.dto.response;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import java.util.UUID;

public record MapEdgeResponse(
        UUID id,
        UUID fromNodeId,
        UUID toNodeId,
        double distance,
        boolean bidirectional
) {
    public static MapEdgeResponse from(MapEdge edge) {
        return new MapEdgeResponse(
                edge.getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getDistance(),
                edge.isBidirectional()
        );
    }
}
