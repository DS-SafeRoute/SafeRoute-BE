package com.saferoute.domain.evacuation.service;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import java.util.List;

// 다익스트라 계산 결과: 시작 노드부터 도착 EXIT 노드까지의 경로와 총 가중치
public record EvacuationRoute(List<MapNode> path, double totalWeight) {
}
