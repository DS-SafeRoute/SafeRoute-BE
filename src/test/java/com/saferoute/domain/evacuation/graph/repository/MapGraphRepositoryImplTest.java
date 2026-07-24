package com.saferoute.domain.evacuation.graph.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("두 노드를 엣지로 연결하면 거리 속성이 저장된다")
    void addEdge_success() {
        // given
        Floor floor = mock(Floor.class);
        MapNode from = mock(MapNode.class);
        MapNode to = mock(MapNode.class);
        MapEdge savedEdge = MapEdge.create(floor, from, to, 8.0);
        given(mapEdgeJpaRepository.save(any(MapEdge.class))).willReturn(savedEdge);

        // when
        MapEdge result = mapGraphRepository.addEdge(floor, from, to, 8.0);

        // then
        assertThat(result.getDistance()).isEqualTo(8.0);
        assertThat(result.isBlocked()).isFalse();
        verify(mapEdgeJpaRepository).save(any(MapEdge.class));
    }

    @Test
    @DisplayName("층별로 노드와 엣지를 조회할 수 있다")
    void findByFloor_success() {
        // given
        MapNode node = mock(MapNode.class);
        MapEdge edge = mock(MapEdge.class);
        given(mapNodeJpaRepository.findByFloorId(floorId)).willReturn(List.of(node));
        given(mapEdgeJpaRepository.findByFloorId(floorId)).willReturn(List.of(edge));

        // when
        List<MapNode> nodes = mapGraphRepository.findNodesByFloor(floorId);
        List<MapEdge> edges = mapGraphRepository.findEdgesByFloor(floorId);

        // then
        assertThat(nodes).hasSize(1);
        assertThat(edges).hasSize(1);
    }
}
