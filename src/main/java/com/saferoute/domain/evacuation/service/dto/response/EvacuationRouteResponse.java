package com.saferoute.domain.evacuation.service.dto.response;

import com.saferoute.domain.evacuation.graph.dto.response.MapNodeResponse;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import java.util.List;

public record EvacuationRouteResponse(
        List<MapNodeResponse> path,
        double totalWeight
) {
    public static EvacuationRouteResponse from(EvacuationRoute route) {
        return new EvacuationRouteResponse(
                route.path().stream().map(MapNodeResponse::from).toList(),
                route.totalWeight()
        );
    }
}
