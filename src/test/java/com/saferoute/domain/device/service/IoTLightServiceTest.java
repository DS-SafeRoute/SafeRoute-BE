package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
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
class IoTLightServiceTest {

    @InjectMocks
    private IoTLightService iotLightService;

    @Mock
    private IoTLightJpaRepository iotLightJpaRepository;

    @Mock
    private MapNodeJpaRepository mapNodeJpaRepository;

    @Mock
    private MapEdgeJpaRepository mapEdgeJpaRepository;

    @Mock
    private FloorRepository floorRepository;

    private final UUID floorId = UUID.randomUUID();
    private Floor floor;

    @BeforeEach
    void setUp() {
        floor = mock(Floor.class);
    }

    private MapNode createNode(String code, NodeType type) {
        MapNode node = type == NodeType.CUSTOM
                ? MapNode.createCustom(floor, code, code, 0, 0)
                : MapNode.create(floor, code, type, code, 0, 0, false);
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        return node;
    }

    private MapEdge createEdge(MapNode from, MapNode to) {
        MapEdge edge = MapEdge.create(floor, from, to, 3.0, true);
        ReflectionTestUtils.setField(edge, "id", UUID.randomUUID());
        return edge;
    }

    private IoTLight createLight(String code, MapNode customNode) {
        IoTLight light = IoTLight.create(code, code, customNode);
        ReflectionTestUtils.setField(light, "id", UUID.randomUUID());
        return light;
    }

    // === createLight ===

    @Test
    @DisplayName("유도등을 등록하면 도면 위치 노드가 함께 생성된다")
    void createLight_success() {
        // given
        CreateIoTLightRequest request = new CreateIoTLightRequest(floorId, "복도1 유도등", 0.3, 0.4);
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight savedLight = createLight("LIGHT_001", customNode);

        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));
        given(iotLightJpaRepository.count()).willReturn(0L);
        given(mapNodeJpaRepository.save(any(MapNode.class))).willReturn(customNode);
        given(iotLightJpaRepository.save(any(IoTLight.class))).willReturn(savedLight);

        // when
        IoTLightResponse response = iotLightService.createLight(request);

        // then
        assertThat(response.code()).isEqualTo("LIGHT_001");
        assertThat(response.enabled()).isTrue();
        assertThat(response.guidanceConfigured()).isFalse();
    }

    @Test
    @DisplayName("등록할 때마다 code가 순번대로 자동 생성된다")
    void createLight_generatesSequentialCode() {
        // given
        CreateIoTLightRequest request = new CreateIoTLightRequest(floorId, "복도1 유도등", 0.3, 0.4);
        MapNode customNode = createNode("LIGHT_006", NodeType.CUSTOM);
        IoTLight savedLight = createLight("LIGHT_006", customNode);

        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));
        given(iotLightJpaRepository.count()).willReturn(5L);
        given(mapNodeJpaRepository.save(any(MapNode.class))).willReturn(customNode);
        given(iotLightJpaRepository.save(any(IoTLight.class))).willReturn(savedLight);

        // when
        IoTLightResponse response = iotLightService.createLight(request);

        // then
        assertThat(response.code()).isEqualTo("LIGHT_006");
    }

    @Test
    @DisplayName("존재하지 않는 층에 등록하려 하면 예외가 발생한다")
    void createLight_floorNotFound_throws() {
        // given
        CreateIoTLightRequest request = new CreateIoTLightRequest(floorId, "복도1 유도등", 0.3, 0.4);
        given(floorRepository.findById(floorId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> iotLightService.createLight(request))
                .isInstanceOf(ApiException.class)
                .hasMessage(FloorErrorCode.FLOOR_NOT_FOUND.getMessage());
    }

    // === getLights / getLight ===

    @Test
    @DisplayName("층 ID로 유도등 목록을 조회한다")
    void getLights_byFloor_success() {
        // given
        MapNode node = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", node);
        given(iotLightJpaRepository.findAllByCustomNode_Floor_Id(floorId)).willReturn(List.of(light));

        // when
        List<IoTLightResponse> responses = iotLightService.getLights(floorId);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 유도등을 조회하면 예외가 발생한다")
    void getLight_notFound_throws() {
        // given
        UUID unknownId = UUID.randomUUID();
        given(iotLightJpaRepository.findById(unknownId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> iotLightService.getLight(unknownId))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.IOT_LIGHT_NOT_FOUND.getMessage());
    }

    // === configureGuidance ===

    @Test
    @DisplayName("decisionNode에 연결된 엣지로 분기 경로를 설정한다")
    void configureGuidance_success() {
        // given
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", customNode);
        MapNode decisionNode = createNode("HALLWAY1", NodeType.HALLWAY);
        MapNode leftTarget = createNode("HALLWAY2", NodeType.HALLWAY);
        MapNode rightTarget = createNode("HALLWAY3", NodeType.HALLWAY);
        MapEdge leftEdge = createEdge(decisionNode, leftTarget);
        MapEdge rightEdge = createEdge(decisionNode, rightTarget);

        ConfigureGuidanceRequest request =
                new ConfigureGuidanceRequest(decisionNode.getId(), leftEdge.getId(), rightEdge.getId());

        given(iotLightJpaRepository.findById(light.getId())).willReturn(Optional.of(light));
        given(mapNodeJpaRepository.findById(decisionNode.getId())).willReturn(Optional.of(decisionNode));
        given(mapEdgeJpaRepository.findById(leftEdge.getId())).willReturn(Optional.of(leftEdge));
        given(mapEdgeJpaRepository.findById(rightEdge.getId())).willReturn(Optional.of(rightEdge));

        // when
        IoTLightResponse response = iotLightService.configureGuidance(light.getId(), request);

        // then
        assertThat(response.guidanceConfigured()).isTrue();
        assertThat(response.decisionNodeId()).isEqualTo(decisionNode.getId());
    }

    @Test
    @DisplayName("decisionNode에 연결되지 않은 엣지로 설정하려 하면 예외가 발생한다")
    void configureGuidance_edgeNotConnectedToDecisionNode_throws() {
        // given
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", customNode);
        MapNode decisionNode = createNode("HALLWAY1", NodeType.HALLWAY);
        MapNode unrelatedA = createNode("HALLWAY2", NodeType.HALLWAY);
        MapNode unrelatedB = createNode("HALLWAY3", NodeType.HALLWAY);
        MapEdge unrelatedEdge = createEdge(unrelatedA, unrelatedB); // decisionNode와 무관한 엣지
        MapNode rightTarget = createNode("HALLWAY4", NodeType.HALLWAY);
        MapEdge rightEdge = createEdge(decisionNode, rightTarget);

        ConfigureGuidanceRequest request =
                new ConfigureGuidanceRequest(decisionNode.getId(), unrelatedEdge.getId(), rightEdge.getId());

        given(iotLightJpaRepository.findById(light.getId())).willReturn(Optional.of(light));
        given(mapNodeJpaRepository.findById(decisionNode.getId())).willReturn(Optional.of(decisionNode));
        given(mapEdgeJpaRepository.findById(unrelatedEdge.getId())).willReturn(Optional.of(unrelatedEdge));
        given(mapEdgeJpaRepository.findById(rightEdge.getId())).willReturn(Optional.of(rightEdge));

        // when & then
        assertThatThrownBy(() -> iotLightService.configureGuidance(light.getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.INVALID_GUIDANCE_EDGE.getMessage());
    }

    @Test
    @DisplayName("leftEdge와 rightEdge가 같으면 예외가 발생한다 (500이 아닌 400)")
    void configureGuidance_sameLeftAndRightEdge_throws() {
        // given
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", customNode);
        UUID decisionNodeId = UUID.randomUUID();
        UUID sameEdgeId = UUID.randomUUID();
        ConfigureGuidanceRequest request = new ConfigureGuidanceRequest(decisionNodeId, sameEdgeId, sameEdgeId);

        given(iotLightJpaRepository.findById(light.getId())).willReturn(Optional.of(light));

        // when & then: decisionNode/엣지 조회 전에 걸러지므로 관련 mock 없이도 검증 가능
        assertThatThrownBy(() -> iotLightService.configureGuidance(light.getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.INVALID_GUIDANCE_EDGE.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 decisionNode로 설정하려 하면 예외가 발생한다")
    void configureGuidance_decisionNodeNotFound_throws() {
        // given
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", customNode);
        UUID unknownNodeId = UUID.randomUUID();
        ConfigureGuidanceRequest request =
                new ConfigureGuidanceRequest(unknownNodeId, UUID.randomUUID(), UUID.randomUUID());

        given(iotLightJpaRepository.findById(light.getId())).willReturn(Optional.of(light));
        given(mapNodeJpaRepository.findById(unknownNodeId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> iotLightService.configureGuidance(light.getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.DECISION_NODE_NOT_FOUND.getMessage());
    }

    // === updateLight ===

    @Test
    @DisplayName("이름과 위치를 수정한다")
    void updateLight_success() {
        // given
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", customNode);
        UpdateIoTLightRequest request = new UpdateIoTLightRequest("변경된 이름", 0.7, 0.8);
        given(iotLightJpaRepository.findById(light.getId())).willReturn(Optional.of(light));

        // when
        IoTLightResponse response = iotLightService.updateLight(light.getId(), request);

        // then
        assertThat(response.name()).isEqualTo("변경된 이름");
        assertThat(response.x()).isEqualTo(0.7);
        assertThat(response.y()).isEqualTo(0.8);
    }

    // === enable / disable ===

    @Test
    @DisplayName("유도등을 비활성화한다")
    void disableLight_success() {
        // given
        MapNode customNode = createNode("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = createLight("LIGHT_001", customNode);
        given(iotLightJpaRepository.findById(light.getId())).willReturn(Optional.of(light));

        // when
        IoTLightResponse response = iotLightService.disableLight(light.getId());

        // then
        assertThat(response.enabled()).isFalse();
    }
}
