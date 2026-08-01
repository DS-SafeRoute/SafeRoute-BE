package com.saferoute.domain.evacuation.graph.dto.response;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import java.util.List;

// 커스텀 편집 UI가 캔버스를 그릴 때 쓰는 층별 그래프 전체 응답
public record FloorGraphResponse(
        List<MapNodeResponse> nodes,
        List<MapEdgeResponse> edges
) {
    public static FloorGraphResponse of(List<MapNode> nodes, List<MapEdge> edges) {
        return new FloorGraphResponse(
                nodes.stream().map(MapNodeResponse::from).toList(),
                edges.stream().map(MapEdgeResponse::from).toList()
        );
    }
}
