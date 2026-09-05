package com.saferoute.domain.evacuation.graph.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MapGraphRepositoryImplTest {

    @InjectMocks
    private MapGraphRepositoryImpl mapGraphRepository;

    @Mock
    private MapNodeJpaRepository mapNodeJpaRepository;

    @Mock
    private MapEdgeJpaRepository mapEdgeJpaRepository;

    private final UUID floorId = UUID.randomUUID();

    @Test
    @DisplayName("노드를 추가하면 저장된다")
    void addNode_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode savedNode = MapNode.create(floor, "STAIR_B", NodeType.STAIR, "계단B", 0, 0, true);
        given(mapNodeJpaRepository.save(any(MapNode.class))).willReturn(savedNode);

        // when
        MapNode result = mapGraphRepository.addNode(floor, "STAIR_B", NodeType.STAIR, "계단B", 0, 0, true);

        // then
        assertThat(result.getCode()).isEqualTo("STAIR_B");
        assertThat(result.isExitTarget()).isTrue();
        verify(mapNodeJpaRepository).save(any(MapNode.class));
    }

    @Test
    @DisplayName("두 노드를 엣지로 연결하면 거리/수용인원/양방향 속성이 저장된다")
    void addEdge_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode from = mock(MapNode.class);
        MapNode to = mock(MapNode.class);
        MapEdge savedEdge = MapEdge.create(floor, from, to, 8.0,true);
        given(mapEdgeJpaRepository.save(any(MapEdge.class))).willReturn(savedEdge);

        // when
        MapEdge result = mapGraphRepository.addEdge(floor, from, to, 8.0,true);

        // then
        assertThat(result.getDistance()).isEqualTo(8.0);
        assertThat(result.isBidirectional()).isTrue();
        verify(mapEdgeJpaRepository).save(any(MapEdge.class));
    }

    @Test
    @DisplayName("층별로 노드와 엣지를 조회할 수 있다")
    void findByFloor_success() {
        // given
        MapNode node = mock(MapNode.class);
        MapEdge edge = mock(MapEdge.class);
        given(mapNodeJpaRepository.findAllByFloor_Id(floorId)).willReturn(List.of(node));
        given(mapEdgeJpaRepository.findAllByFloor_Id(floorId)).willReturn(List.of(edge));

        // when
        List<MapNode> nodes = mapGraphRepository.findNodesByFloor(floorId);
        List<MapEdge> edges = mapGraphRepository.findEdgesByFloor(floorId);

        // then
        assertThat(nodes).hasSize(1);
        assertThat(edges).hasSize(1);
    }

    @Test
    @DisplayName("distance가 0 이하면 400으로 거부된다")
    void addEdge_nonPositiveDistance_throws() {
        // given
        Floor floor = mock(Floor.class);
        MapNode from = mock(MapNode.class);
        MapNode to = mock(MapNode.class);

        // when & then
        assertThatThrownBy(() -> mapGraphRepository.addEdge(floor, from, to, 0.0, true))
                .isInstanceOf(ApiException.class)
                .hasMessage(EvacuationErrorCode.INVALID_MAP_EDGE_DISTANCE.getMessage());
        verify(mapEdgeJpaRepository, never()).save(any(MapEdge.class));
    }

    @Test
    @DisplayName("ROOM 노드는 DOOR가 아닌 노드와 직접 연결할 수 없다")
    void addEdge_roomWithoutDoor_throws() {
        // given
        Floor floor = mock(Floor.class);
        MapNode room = mock(MapNode.class);
        MapNode hallway = mock(MapNode.class);
        given(room.getType()).willReturn(NodeType.ROOM);
        given(hallway.getType()).willReturn(NodeType.HALLWAY);

        // when & then
        assertThatThrownBy(() -> mapGraphRepository.addEdge(floor, room, hallway, 3.0, true))
                .isInstanceOf(ApiException.class)
                .hasMessage(EvacuationErrorCode.INVALID_MAP_EDGE_CONNECTION.getMessage());
    }

    @Test
    @DisplayName("ROOM-DOOR 연결은 허용된다")
    void addEdge_roomWithDoor_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode room = mock(MapNode.class);
        MapNode door = mock(MapNode.class);
        given(room.getType()).willReturn(NodeType.ROOM);
        given(door.getType()).willReturn(NodeType.DOOR);
        MapEdge savedEdge = MapEdge.create(floor, room, door, 2.0, true);
        given(mapEdgeJpaRepository.save(any(MapEdge.class))).willReturn(savedEdge);

        // when
        MapEdge result = mapGraphRepository.addEdge(floor, room, door, 2.0, true);

        // then
        assertThat(result.getDistance()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("이미 연결된 노드 쌍은 다시 연결할 수 없다 (역방향도 동일하게 차단)")
    void addEdge_duplicate_throws() {
        // given
        Floor floor = mock(Floor.class);
        MapNode nodeA = mock(MapNode.class);
        MapNode nodeB = mock(MapNode.class);
        UUID nodeAId = UUID.randomUUID();
        UUID nodeBId = UUID.randomUUID();
        given(nodeA.getId()).willReturn(nodeAId);
        given(nodeB.getId()).willReturn(nodeBId);
        given(mapEdgeJpaRepository.existsByFromNode_IdAndToNode_IdOrFromNode_IdAndToNode_Id(
                nodeAId, nodeBId, nodeBId, nodeAId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> mapGraphRepository.addEdge(floor, nodeA, nodeB, 3.0, true))
                .isInstanceOf(ApiException.class)
                .hasMessage(EvacuationErrorCode.DUPLICATE_MAP_EDGE.getMessage());
    }

    @Test
    @DisplayName("노드 위치와 EXIT 대상 여부를 수정하면 dirty checking으로 반영된다 (save 호출 없음)")
    void updateNodePosition_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode node = MapNode.create(floor, "ROOM1", NodeType.ROOM, "방1", 0, 0, false);

        // when
        MapNode result = mapGraphRepository.updateNodePosition(node, 10, 20, true);

        // then
        assertThat(result.getX()).isEqualTo(10);
        assertThat(result.getY()).isEqualTo(20);
        assertThat(result.isExitTarget()).isTrue();
        verify(mapNodeJpaRepository, never()).save(any(MapNode.class));
    }

    @Test
    @DisplayName("DOOR 노드의 시작 후보 여부를 토글하면 dirty checking으로 반영된다 (save 호출 없음)")
    void updateStartCandidate_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode node = MapNode.create(floor, "DOOR1", NodeType.DOOR, "출입문", 0, 0, false);

        // when
        MapNode result = mapGraphRepository.updateStartCandidate(node, true);

        // then
        assertThat(result.isStartCandidate()).isTrue();
        verify(mapNodeJpaRepository, never()).save(any(MapNode.class));
    }

    @Test
    @DisplayName("노드를 삭제하면 연결된 엣지가 먼저 삭제된 후 노드가 삭제된다")
    void deleteNode_cascadesConnectedEdges() {
        // given
        Floor floor = mock(Floor.class);
        MapNode node = MapNode.create(floor, "ROOM1", NodeType.ROOM, "방1", 0, 0, false);
        UUID nodeId = UUID.randomUUID();
        ReflectionTestUtils.setField(node, "id", nodeId);

        // when
        mapGraphRepository.deleteNode(node);

        // then: 엣지 삭제가 노드 삭제보다 먼저 호출돼야 FK 제약조건 위반이 안 남
        InOrder inOrder = inOrder(mapEdgeJpaRepository, mapNodeJpaRepository);
        inOrder.verify(mapEdgeJpaRepository).deleteByFromNode_IdOrToNode_Id(nodeId, nodeId);
        inOrder.verify(mapNodeJpaRepository).delete(node);
    }

    @Test
    @DisplayName("엣지를 삭제하면 Repository에 삭제가 위임된다")
    void deleteEdge_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode from = mock(MapNode.class);
        MapNode to = mock(MapNode.class);
        MapEdge edge = MapEdge.create(floor, from, to, 5.0, true);

        // when
        mapGraphRepository.deleteEdge(edge);

        // then
        verify(mapEdgeJpaRepository).delete(edge);
    }
}
