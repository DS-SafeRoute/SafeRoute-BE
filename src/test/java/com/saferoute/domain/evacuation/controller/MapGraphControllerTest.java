package com.saferoute.domain.evacuation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.evacuation.graph.dto.response.FloorGraphResponse;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.service.MapGraphService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;

import com.saferoute.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = EvacuationRouteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false) // Security 필터는 HTTP 매핑/예외 응답 검증과 무관하니 비활성화
class MapGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapGraphService mapGraphService;

    private final UUID floorId = UUID.randomUUID();
    private Floor floor;
    private MapNode room1;
    private MapNode door1;

    @BeforeEach
    void setUp() {
        floor = mock(Floor.class);
        room1 = createNode("ROOM1", NodeType.ROOM, false);
        door1 = createNode("DOOR1", NodeType.DOOR, true);
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
    @DisplayName("GET /graph - 층의 노드/엣지 전체를 반환한다")
    void getGraph_success() throws Exception {
        // given
        MapEdge edge = createEdge(room1, door1, 2);
        given(mapGraphService.getFloorGraph(floorId))
                .willReturn(FloorGraphResponse.of(List.of(room1, door1), List.of(edge)));

        // when & then
        mockMvc.perform(get("/api/v1/floors/{floorId}/graph", floorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.nodes.length()").value(2))
                .andExpect(jsonPath("$.result.nodes[0].code").value("ROOM1"))
                .andExpect(jsonPath("$.result.edges.length()").value(1))
                .andExpect(jsonPath("$.result.edges[0].distance").value(2));
    }

    @Test
    @DisplayName("GET /graph - 층이 없으면 404를 반환한다")
    void getGraph_floorNotFound() throws Exception {
        // given
        given(mapGraphService.getFloorGraph(floorId)).willThrow(new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/floors/{floorId}/graph", floorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.message", containsString("도면을 찾을 수 없습니다")));
    }
}
