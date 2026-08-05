package com.saferoute.domain.evacuation.service;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapGraphRepository;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvacuationRouteService {

    // TODO: 혼잡도 가중치 계수 - DynamoDB 혼잡도 연동 인터페이스 확정 후 담당 팀원과 조율
    private static final double CONGESTION_WEIGHT = 1.0; // α
    // TODO: 위험도(화재 확산 단계) 가중치 계수 - 확정 필요
    private static final double DANGER_WEIGHT = 1.0; // β

    private final MapGraphRepository mapGraphRepository;

    // 시작 노드에서 가장 가까운 EXIT 대상 노드까지 최단 경로 계산 (mock data 기준, congestion/danger는 0 고정)
    public EvacuationRoute findShortestRoute(UUID floorId, UUID startNodeId) {
        List<MapNode> nodes = mapGraphRepository.findNodesByFloor(floorId);
        List<MapEdge> edges = mapGraphRepository.findEdgesByFloor(floorId);

        Map<UUID, MapNode> nodeById = new HashMap<>();
        for (MapNode node : nodes) {
            nodeById.put(node.getId(), node);
        }
        if (!nodeById.containsKey(startNodeId)) {
            throw new ApiException(EvacuationErrorCode.MAP_NODE_NOT_FOUND);
        }

        // 층에 EXIT 대상 노드가 하나도 없는 경우 - "도달 불가"(EVAC005)와 구분되는 별도 원인이므로 먼저 검증
        if (nodes.stream().noneMatch(MapNode::isExitTarget)) {
            throw new ApiException(EvacuationErrorCode.EXIT_NODE_NOT_DESIGNATED);
        }

        Map<UUID, List<MapEdge>> adjacency = buildAdjacencyList(edges);

        Map<UUID, Double> distance = new HashMap<>();
        Map<UUID, UUID> previous = new HashMap<>();
        distance.put(startNodeId, 0.0);

        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        queue.add(new NodeDistance(startNodeId, 0.0));
        Set<UUID> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (!visited.add(current.nodeId())) {
                continue;
            }

            MapNode currentNode = nodeById.get(current.nodeId());
            if (currentNode.isExitTarget()) {
                return buildRoute(nodeById, previous, distance, current.nodeId());
            }

            for (MapEdge edge : adjacency.getOrDefault(current.nodeId(), Collections.emptyList())) {
                // TODO: blocked 상태는 더 이상 MapEdge 컬럼이 아니라 훈련별 서버 메모리에서 관리됨 -
                // 런타임 blocked 데이터 소스 연동 확정 후 여기서 제외 처리 추가 필요
                UUID neighbor = edge.getToNode().getId().equals(current.nodeId())
                        ? edge.getFromNode().getId()
                        : edge.getToNode().getId();

                double newDistance = distance.get(current.nodeId()) + calculateWeight(edge);
                if (newDistance < distance.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    distance.put(neighbor, newDistance);
                    previous.put(neighbor, current.nodeId());
                    queue.add(new NodeDistance(neighbor, newDistance));
                }
            }
        }

        throw new ApiException(EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND);
    }

    // bidirectional 엣지만 양방향 인접 리스트에 등록, 단방향 엣지는 fromNode -> toNode 방향으로만 등록
    private Map<UUID, List<MapEdge>> buildAdjacencyList(List<MapEdge> edges) {
        Map<UUID, List<MapEdge>> adjacency = new HashMap<>();
        for (MapEdge edge : edges) {
            adjacency.computeIfAbsent(edge.getFromNode().getId(), k -> new ArrayList<>()).add(edge);
            if (edge.isBidirectional()) {
                adjacency.computeIfAbsent(edge.getToNode().getId(), k -> new ArrayList<>()).add(edge);
            }
        }
        return adjacency;
    }

    // weight = distance + α×congestion + β×danger
    // TODO: congestion/danger 현재 0 고정 - 혼잡도(DynamoDB)·blocked 외 danger 데이터 연동 확정 후 반영
    private double calculateWeight(MapEdge edge) {
        double congestion = 0.0;
        double danger = 0.0;
        return edge.getDistance() + CONGESTION_WEIGHT * congestion + DANGER_WEIGHT * danger;
    }

    private EvacuationRoute buildRoute(Map<UUID, MapNode> nodeById, Map<UUID, UUID> previous,
            Map<UUID, Double> distance, UUID exitNodeId) {
        List<MapNode> path = new ArrayList<>();
        UUID current = exitNodeId;
        while (current != null) {
            path.add(nodeById.get(current));
            current = previous.get(current);
        }
        Collections.reverse(path);
        return new EvacuationRoute(path, distance.get(exitNodeId));
    }

    private record NodeDistance(UUID nodeId, double distance) {
    }
}
