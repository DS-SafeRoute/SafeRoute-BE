package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
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
    public MapEdge addEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance, boolean bidirectional) {
        validateConnection(fromNode, toNode);
        validateNotDuplicate(fromNode, toNode);
        MapEdge edge = MapEdge.create(floor, fromNode, toNode, distance, bidirectional);
        return mapEdgeJpaRepository.save(edge);
    }

    // 동일 노드 쌍(A-B, B-A 포함) 사이에는 엣지를 하나만 허용
    private void validateNotDuplicate(MapNode fromNode, MapNode toNode) {
        boolean exists = mapEdgeJpaRepository.existsByFromNode_IdAndToNode_IdOrFromNode_IdAndToNode_Id(
                fromNode.getId(), toNode.getId(), toNode.getId(), fromNode.getId());
        if (exists) {
            throw new ApiException(EvacuationErrorCode.DUPLICATE_MAP_EDGE);
        }
    }

    // ROOM 노드는 DOOR 노드를 거치지 않고는 다른 노드(HALLWAY 등)와 직접 연결될 수 없다
    private void validateConnection(MapNode fromNode, MapNode toNode) {
        boolean fromIsRoom = fromNode.getType() == NodeType.ROOM;
        boolean toIsRoom = toNode.getType() == NodeType.ROOM;

        if (fromIsRoom && toNode.getType() != NodeType.DOOR) {
            throw new ApiException(EvacuationErrorCode.INVALID_MAP_EDGE_CONNECTION);
        }
        if (toIsRoom && fromNode.getType() != NodeType.DOOR) {
            throw new ApiException(EvacuationErrorCode.INVALID_MAP_EDGE_CONNECTION);
        }
    }

    @Override
    public List<MapNode> findNodesByFloor(UUID floorId) {
        return mapNodeJpaRepository.findAllByFloor_Id(floorId);
    }

    @Override
    public List<MapEdge> findEdgesByFloor(UUID floorId) {
        return mapEdgeJpaRepository.findAllByFloor_Id(floorId);
    }

    @Override
    public Optional<MapNode> findNodeById(UUID nodeId) {
        return mapNodeJpaRepository.findById(nodeId);
    }

    @Override
    public long countExitTargetNodesByFloor(UUID floorId) {
        return mapNodeJpaRepository.countByFloor_IdAndIsExitTargetTrue(floorId);
    }

    @Override
    public boolean existsNodeByFloorAndType(UUID floorId, NodeType type) {
        return mapNodeJpaRepository.existsByFloor_IdAndType(floorId, type);
    }

    @Override
    public Optional<MapEdge> findEdgeById(UUID edgeId) {
        return mapEdgeJpaRepository.findById(edgeId);
    }

    @Override
    public MapNode updateNodePosition(MapNode node, double x, double y, boolean isExitTarget) {
        node.moveTo(x, y);
        node.changeExitTarget(isExitTarget);
        return node; // 영속 상태 엔티티라 dirty checking으로 트랜잭션 커밋 시 반영됨
    }

    @Override
    public void deleteNode(MapNode node) {
        // 노드 삭제 시 연결된 엣지(from/to 어느 쪽이든) 먼저 cascade 삭제
        mapEdgeJpaRepository.deleteByFromNode_IdOrToNode_Id(node.getId(), node.getId());
        mapNodeJpaRepository.delete(node);
    }

    @Override
    public void deleteEdge(MapEdge edge) {
        mapEdgeJpaRepository.delete(edge);
    }
}
