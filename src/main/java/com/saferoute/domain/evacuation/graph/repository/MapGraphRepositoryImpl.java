package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MapGraphRepositoryImpl implements MapGraphRepository {

    private final MapNodeJpaRepository mapNodeJpaRepository;
    private final MapEdgeJpaRepository mapEdgeJpaRepository;

    @Override
    public MapNode addNode(Floor floor, String code, NodeType type, String name,
            double x, double y, boolean isExitTarget) {
        MapNode node = MapNode.create(floor, code, type, name, x, y, isExitTarget);
        return mapNodeJpaRepository.save(node);
    }

    @Override
    public MapEdge addEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance, int capacity, boolean bidirectional) {
        MapEdge edge = MapEdge.create(floor, fromNode, toNode, distance, capacity, bidirectional);
        return mapEdgeJpaRepository.save(edge);
    }

    @Override
    public List<MapNode> findNodesByFloor(UUID floorId) {
        return mapNodeJpaRepository.findAllByFloor_Id(floorId);
    }

    @Override
    public List<MapEdge> findEdgesByFloor(UUID floorId) {
        return mapEdgeJpaRepository.findAllByFloor_Id(floorId);
    }
}
