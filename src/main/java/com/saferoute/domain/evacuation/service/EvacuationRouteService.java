package com.saferoute.domain.evacuation.service;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapGraphRepository;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.FloorErrorCode;
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
    private final FloorRepository floorRepository;
    private final SchoolContextService schoolContextService;

    // 시작 노드에서 가장 가까운 EXIT 대상 노드까지 최단 경로 계산 (mock data 기준, congestion/danger는 0 고정)
    public EvacuationRoute findShortestRoute(UUID floorId, UUID startNodeId) {
        return findShortestRoute(floorId, startNodeId, Set.of());
    }

    public EvacuationRoute findShortestRoute(UUID floorId, UUID startNodeId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        if (floorRepository.findByIdAndBuilding_SchoolName(floorId, schoolName).isEmpty()) {
            throw new ApiException(FloorErrorCode.FLOOR_NOT_FOUND);
        }
        return findShortestRoute(floorId, startNodeId);
    }

    // 혼잡 재탐색용 - 지정된 엣지를 그래프에서 제외하고 우회 경로를 계산한다 (VERY_CROWDED처럼 완전히 막힌 경우).
    // 레벨별로 페널티만 주고 여전히 후보에 남기고 싶다면 weightMultipliers를 쓰는 4-인자 오버로드를 사용한다.
    public EvacuationRoute findShortestRoute(UUID floorId, UUID startNodeId, Set<UUID> excludedEdgeIds) {
        return findShortestRoute(floorId, startNodeId, excludedEdgeIds, Map.of());
    }

    // 혼잡 단계별로 특정 엣지의 가중치에 배율을 적용한다 (CAUTION ×1.5, CROWDED ×3.0). 
    // 배율이 없는 엣지는 1.0을 적용한 것과 같다. 
    // VERY_CROWDED처럼 아예 후보에서 빼야 하는 경우는 배율이 아니라 excludedEdgeIds로 처리한다 
    // -> 배율만으로는 그래프가 다른 대안이 없을 때 여전히 그 엣지를 통과하는 경로를 고를 수 있기 때문이다.
    public EvacuationRoute findShortestRoute(
            UUID floorId, UUID startNodeId, Set<UUID> excludedEdgeIds, Map<UUID, Double> weightMultipliers
    ) {
        List<MapNode> nodes = mapGraphRepository.findNodesByFloor(floorId);
        List<MapEdge> edges = mapGraphRepository.findEdgesByFloor(floorId).stream()
                .filter(edge -> !excludedEdgeIds.contains(edge.getId()))
                .toList();

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

                double newDistance = distance.get(current.nodeId()) + calculateWeight(edge, weightMultipliers);
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

    // weight = (distance + α×congestion + β×danger) × 혼잡 재탐색용 배율
    // TODO: congestion/danger 현재 0 고정 - 혼잡도(DynamoDB)·blocked 외 danger 데이터 연동 확정 후 반영.
    // weightMultipliers는 그것과 별개로, RouteRecalculationService가 트리거 엣지에 "경로 혼잡 비용"
    // (CAUTION ×1.5, CROWDED ×3.0)를 적용하기 위해 넘기는 값이다 - 두 메커니즘은 독립적이다.
    private double calculateWeight(MapEdge edge, Map<UUID, Double> weightMultipliers) {
        double congestion = 0.0;
        double danger = 0.0;
        double baseWeight = edge.getDistance() + CONGESTION_WEIGHT * congestion + DANGER_WEIGHT * danger;
        return baseWeight * weightMultipliers.getOrDefault(edge.getId(), 1.0);
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
