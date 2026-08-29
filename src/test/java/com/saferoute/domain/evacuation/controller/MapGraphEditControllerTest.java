package com.saferoute.domain.evacuation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.evacuation.graph.dto.request.CreateMapEdgeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.CreateMapNodeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.UpdateMapNodePositionRequest;
import com.saferoute.domain.evacuation.graph.dto.response.MapEdgeResponse;
import com.saferoute.domain.evacuation.graph.dto.response.MapNodeResponse;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.service.MapGraphService;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(
        username = "manager@test.com",
        roles = "MANAGER"
)
class MapGraphEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MapGraphService mapGraphService;

    private final UUID floorId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();
    private final UUID edgeId = UUID.randomUUID();

    @Test
    @WithMockUser(username = "normal@test.com", roles = "NORMAL")
    @DisplayName("일반 사용자는 노드를 생성할 수 없다")
    void createNode_normalUser_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/floors/{floorId}/nodes", floorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "code": "ROOM1",
                              "type": "ROOM",
                              "name": "방1",
                              "x": 0,
                              "y": 0,
                              "isExitTarget": false
                            }
                            """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /nodes - 노드를 생성하면 201을 반환한다")
    void createNode_success() throws Exception {
        CreateMapNodeRequest request = new CreateMapNodeRequest(
                "ROOM1",
                NodeType.ROOM,
                "방1",
                0.0,
                0.0,
                false
        );

        MapNodeResponse response = new MapNodeResponse(
                nodeId,
                "ROOM1",
                NodeType.ROOM,
                "방1",
                0.0,
                0.0,
                false
        );

        given(
                mapGraphService.createNode(
                        eq(floorId),
                        any()
                )
        ).willReturn(response);

        mockMvc.perform(
                        post(
                                "/api/v1/floors/{floorId}/nodes",
                                floorId
                        )
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.code").value("ROOM1"));
    }

    @Test
    @DisplayName("POST /nodes - code가 비어있으면 400을 반환한다")
    void createNode_blankCode_returnsBadRequest() throws Exception {
        String invalidJson = """
                {
                  "code": "",
                  "type": "ROOM",
                  "name": "방1",
                  "x": 0,
                  "y": 0,
                  "isExitTarget": false
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/floors/{floorId}/nodes",
                                floorId
                        )
                                .contentType("application/json")
                                .content(invalidJson)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /nodes - 층이 없으면 404를 반환한다")
    void createNode_floorNotFound() throws Exception {
        CreateMapNodeRequest request = new CreateMapNodeRequest(
                "ROOM1",
                NodeType.ROOM,
                "방1",
                0.0,
                0.0,
                false
        );

        given(
                mapGraphService.createNode(
                        eq(floorId),
                        any()
                )
        ).willThrow(
                new ApiException(
                        FloorErrorCode.FLOOR_NOT_FOUND
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/floors/{floorId}/nodes",
                                floorId
                        )
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath(
                                "$.message",
                                containsString("도면을 찾을 수 없습니다")
                        )
                );
    }

    @Test
    @DisplayName("PATCH /nodes/{nodeId} - 위치를 수정하면 200을 반환한다")
    void updateNodePosition_success() throws Exception {
        UpdateMapNodePositionRequest request =
                new UpdateMapNodePositionRequest(
                        10.0,
                        20.0,
                        true
                );

        MapNodeResponse response = new MapNodeResponse(
                nodeId,
                "ROOM1",
                NodeType.ROOM,
                "방1",
                10.0,
                20.0,
                true
        );

        given(
                mapGraphService.updateNodePosition(
                        eq(nodeId),
                        any()
                )
        ).willReturn(response);

        mockMvc.perform(
                        patch(
                                "/api/v1/nodes/{nodeId}",
                                nodeId
                        )
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.x").value(10.0))
                .andExpect(jsonPath("$.result.y").value(20.0))
                .andExpect(jsonPath("$.result.isExitTarget").value(true));
    }

    @Test
    @DisplayName("PATCH /nodes/{nodeId} - 노드가 없으면 404를 반환한다")
    void updateNodePosition_notFound() throws Exception {
        UpdateMapNodePositionRequest request =
                new UpdateMapNodePositionRequest(
                        10.0,
                        20.0,
                        true
                );

        given(
                mapGraphService.updateNodePosition(
                        eq(nodeId),
                        any()
                )
        ).willThrow(
                new ApiException(
                        EvacuationErrorCode.MAP_NODE_NOT_FOUND
                )
        );

        mockMvc.perform(
                        patch(
                                "/api/v1/nodes/{nodeId}",
                                nodeId
                        )
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /nodes/{nodeId} - 노드를 삭제하면 200을 반환한다")
    void deleteNode_success() throws Exception {
        mockMvc.perform(
                        delete(
                                "/api/v1/nodes/{nodeId}",
                                nodeId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("DELETE /nodes/{nodeId} - 노드가 없으면 404를 반환한다")
    void deleteNode_notFound() throws Exception {
        Mockito.doThrow(
                new ApiException(
                        EvacuationErrorCode.MAP_NODE_NOT_FOUND
                )
        ).when(mapGraphService).deleteNode(nodeId);

        mockMvc.perform(
                        delete(
                                "/api/v1/nodes/{nodeId}",
                                nodeId
                        )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /nodes/{nodeId} - 마지막 EXIT 노드를 삭제하려 하면 400을 반환한다")
    void deleteNode_lastExitNode_returnsBadRequest() throws Exception {
        // given
        Mockito.doThrow(new ApiException(EvacuationErrorCode.EXIT_NODE_DELETE_NOT_ALLOWED))
                .when(mapGraphService).deleteNode(nodeId);

        // when & then
        mockMvc.perform(delete("/api/v1/nodes/{nodeId}", nodeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("마지막 출구 노드")));
    }

    @Test
    @DisplayName("POST /edges - 엣지를 생성하면 201을 반환한다")
    void createEdge_success() throws Exception {
        UUID fromNodeId = UUID.randomUUID();
        UUID toNodeId = UUID.randomUUID();

        CreateMapEdgeRequest request = new CreateMapEdgeRequest(
                fromNodeId,
                toNodeId,
                5.0,
                true
        );

        MapEdgeResponse response = new MapEdgeResponse(
                edgeId,
                fromNodeId,
                toNodeId,
                5.0,
                true
        );

        given(
                mapGraphService.createEdge(any())
        ).willReturn(response);

        mockMvc.perform(
                        post("/api/v1/edges")
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.distance").value(5.0));
    }

    @Test
    @DisplayName("POST /edges - room-hallway 직접 연결이면 400을 반환한다")
    void createEdge_invalidConnection_returnsBadRequest() throws Exception {
        CreateMapEdgeRequest request = new CreateMapEdgeRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                5.0,
                true
        );

        given(
                mapGraphService.createEdge(any())
        ).willThrow(
                new ApiException(
                        EvacuationErrorCode.INVALID_MAP_EDGE_CONNECTION
                )
        );

        mockMvc.perform(
                        post("/api/v1/edges")
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath(
                                "$.message",
                                containsString("DOOR 노드를 통해서만")
                        )
                );
    }

    @Test
    @DisplayName("DELETE /edges/{edgeId} - 엣지를 삭제하면 200을 반환한다")
    void deleteEdge_success() throws Exception {
        mockMvc.perform(
                        delete(
                                "/api/v1/edges/{edgeId}",
                                edgeId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("DELETE /edges/{edgeId} - 엣지가 없으면 404를 반환한다")
    void deleteEdge_notFound() throws Exception {
        Mockito.doThrow(
                new ApiException(
                        EvacuationErrorCode.MAP_EDGE_NOT_FOUND
                )
        ).when(mapGraphService).deleteEdge(edgeId);

        mockMvc.perform(
                        delete(
                                "/api/v1/edges/{edgeId}",
                                edgeId
                        )
                )
                .andExpect(status().isNotFound());
    }
}