package com.saferoute.domain.evacuation.recalculation.dto.response;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 훈련 세션에 대해 "지금 안내되고 있는" 대피 경로 하나를 도면에 바로 그릴 수 있는 형태로
// 반환한다. 가장 최근 승인된 재탐색이 있으면 그 경로(source=RECALCULATED), 없으면 시나리오의
// 대표 startNode 기준 최단 경로(source=INITIAL)다.
public record CurrentRouteResponse(
        UUID sessionId,
        UUID scenarioId,
        UUID buildingId,
        UUID floorId,
        UUID startNodeId,
        RouteSource source,
        List<NodePoint> path,
        double totalWeight,
        Instant updatedAt
) {

    public enum RouteSource {
        INITIAL,
        RECALCULATED
    }

    public record NodePoint(UUID nodeId, String name, NodeType type, double x, double y) {
        public static NodePoint from(MapNode node) {
            return new NodePoint(node.getId(), node.getName(), node.getType(), node.getX(), node.getY());
        }
    }
}
