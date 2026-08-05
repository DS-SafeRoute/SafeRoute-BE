package com.saferoute.domain.evacuation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapGraphRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EvacuationRouteServiceTest {

    @InjectMocks
    private EvacuationRouteService evacuationRouteService;

    @Mock
    private MapGraphRepository mapGraphRepository;

    private final UUID floorId = UUID.randomUUID();
    private Floor floor;

    // 테스트용 그래프:
    // ROOM1 -- DOOR1 -- HALLWAY1 -- STAIR1(EXIT, 우회 경로)
    //                       |
    //                   HALLWAY2 -- STAIR2(EXIT, 최단 경로)
    private MapNode room1;
    private MapNode door1;
    private MapNode hallway1;
    private MapNode hallway2;
    private MapNode stair1;
    private MapNode stair2;

    @BeforeEach
    void setUp() {
        floor = mock(Floor.class);

        room1 = createNode("ROOM1", NodeType.ROOM, false);
        door1 = createNode("DOOR1", NodeType.DOOR, false);
        hallway1 = createNode("HALLWAY1", NodeType.HALLWAY, false);
        hallway2 = createNode("HALLWAY2", NodeType.HALLWAY, false);
        stair1 = createNode("STAIR1", NodeType.STAIR, true);
        stair2 = createNode("STAIR2", NodeType.STAIR, true);
    }

    private MapNode createNode(String code, NodeType type, boolean isExitTarget) {
        MapNode node = MapNode.create(floor, code, type, code, 0, 0, isExitTarget);
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        return node;
    }

    private MapEdge createEdge(MapNode from, MapNode to, double distance) {
        MapEdge edge = MapEdge.create(floor, from, to, distance, true);
        ReflectionTestUtils.setField(edge, "id", UUID.randomUUID());
        return edge;
    }

    @Test
    @DisplayName("여러 EXIT 후보 중 가장 가까운 경로를 반환한다")
    void findShortestRoute_picksNearestExit() {
        // given
        MapEdge e1 = createEdge(room1, door1, 2);
        MapEdge e2 = createEdge(door1, hallway1, 5);
        MapEdge e3 = createEdge(hallway1, stair1, 10); // 멀리 있는 EXIT
        MapEdge e4 = createEdge(hallway1, hallway2, 3);
        MapEdge e5 = createEdge(hallway2, stair2, 2); // 가까운 EXIT

        given(mapGraphRepository.findNodesByFloor(floorId))
                .willReturn(List.of(room1, door1, hallway1, hallway2, stair1, stair2));
        given(mapGraphRepository.findEdgesByFloor(floorId))
                .willReturn(List.of(e1, e2, e3, e4, e5));

        // when
        EvacuationRoute route = evacuationRouteService.findShortestRoute(floorId, room1.getId());

        // then
        assertThat(route.totalWeight()).isEqualTo(2 + 5 + 3 + 2);
        assertThat(route.path()).containsExactly(room1, door1, hallway1, hallway2, stair2);
    }

    @Test
    @DisplayName("도달 가능한 EXIT이 없으면 예외를 던진다")
    void findShortestRoute_noReachableExit_throws() {
        // given
        MapNode isolatedRoom = createNode("ISOLATED", NodeType.ROOM, false);
        MapNode exitOnly = createNode("EXIT_ONLY", NodeType.STAIR, true);
        // 두 노드 사이 엣지 없음 -> 도달 불가

        given(mapGraphRepository.findNodesByFloor(floorId))
                .willReturn(List.of(isolatedRoom, exitOnly));
        given(mapGraphRepository.findEdgesByFloor(floorId))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> evacuationRouteService.findShortestRoute(floorId, isolatedRoom.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage(EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("층에 지정된 EXIT 노드가 없으면 예외를 던진다")
    void findShortestRoute_noExitDesignated_throws() {
        // given
        MapNode isolatedRoom = createNode("ISOLATED", NodeType.ROOM, false);

        given(mapGraphRepository.findNodesByFloor(floorId)).willReturn(List.of(isolatedRoom));
        given(mapGraphRepository.findEdgesByFloor(floorId)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> evacuationRouteService.findShortestRoute(floorId, isolatedRoom.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage(EvacuationErrorCode.EXIT_NODE_NOT_DESIGNATED.getMessage());
    }

    @Test
    @DisplayName("시작 노드가 해당 층에 없으면 예외를 던진다")
    void findShortestRoute_startNodeNotInFloor_throws() {
        // given
        given(mapGraphRepository.findNodesByFloor(floorId)).willReturn(List.of(room1));
        given(mapGraphRepository.findEdgesByFloor(floorId)).willReturn(List.of());

        UUID unknownNodeId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> evacuationRouteService.findShortestRoute(floorId, unknownNodeId))
                .isInstanceOf(ApiException.class)
                .hasMessage(EvacuationErrorCode.MAP_NODE_NOT_FOUND.getMessage());
    }
}
