package com.saferoute.domain.evacuation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(
        username = "normal@test.com",
        roles = "NORMAL"
)
class EvacuationRouteControllerTest {

    private static final String EMAIL = "normal@test.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvacuationRouteService evacuationRouteService;

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

    private MapNode createNode(
            String code,
            NodeType type,
            boolean isExitTarget
    ) {
        MapNode node = MapNode.create(
                floor,
                code,
                type,
                code,
                0,
                0,
                isExitTarget
        );

        ReflectionTestUtils.setField(
                node,
                "id",
                UUID.randomUUID()
        );

        return node;
    }

    @Test
    @DisplayName("GET /routes - 시작 노드에서 가장 가까운 EXIT까지의 경로를 반환한다")
    void getShortestRoute_success() throws Exception {
        EvacuationRoute route = new EvacuationRoute(
                List.of(room1, door1),
                2.0
        );

        given(
                evacuationRouteService.findShortestRoute(
                        floorId,
                        room1.getId(),
                        EMAIL
                )
        ).willReturn(route);

        mockMvc.perform(
                        get(
                                "/api/v1/floors/{floorId}/routes",
                                floorId
                        ).param(
                                "startNodeId",
                                room1.getId().toString()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.totalWeight").value(2.0))
                .andExpect(jsonPath("$.result.path.length()").value(2))
                .andExpect(jsonPath("$.result.path[1].code").value("DOOR1"));
    }

    @Test
    @DisplayName("GET /routes - 시작 노드가 없으면 404를 반환한다")
    void getShortestRoute_startNodeNotFound() throws Exception {
        UUID unknownNodeId = UUID.randomUUID();

        given(
                evacuationRouteService.findShortestRoute(
                        floorId,
                        unknownNodeId,
                        EMAIL
                )
        ).willThrow(
                new ApiException(
                        EvacuationErrorCode.MAP_NODE_NOT_FOUND
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/floors/{floorId}/routes",
                                floorId
                        ).param(
                                "startNodeId",
                                unknownNodeId.toString()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(
                        jsonPath(
                                "$.message",
                                containsString("노드를 찾을 수 없습니다")
                        )
                );
    }

    @Test
    @DisplayName("GET /routes - 도달 가능한 EXIT이 없으면 404를 반환한다")
    void getShortestRoute_noReachableExit() throws Exception {
        given(
                evacuationRouteService.findShortestRoute(
                        floorId,
                        room1.getId(),
                        EMAIL
                )
        ).willThrow(
                new ApiException(
                        EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/floors/{floorId}/routes",
                                floorId
                        ).param(
                                "startNodeId",
                                room1.getId().toString()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(
                        jsonPath("$.message")
                                .value("도달 가능한 EXIT 노드가 없습니다.")
                );
    }

    @Test
    @DisplayName("GET /routes - 층에 지정된 EXIT 노드가 없으면 404를 반환한다")
    void getShortestRoute_noExitDesignated() throws Exception {
        // given
        given(evacuationRouteService.findShortestRoute(floorId, room1.getId(), EMAIL))
                .willThrow(new ApiException(EvacuationErrorCode.EXIT_NODE_NOT_DESIGNATED));

        // when & then
        mockMvc.perform(get("/api/v1/floors/{floorId}/routes", floorId)
                        .param("startNodeId", room1.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.message").value("지정된 출구 노드가 없습니다."));
    }

    @Test
    @DisplayName("GET /routes - startNodeId 파라미터가 없으면 400을 반환한다")
    void getShortestRoute_missingParam() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/v1/floors/{floorId}/routes",
                                floorId
                        )
                )
                .andExpect(status().isBadRequest());
    }
}
