package com.saferoute.domain.evacuation.deviation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.evacuation.deviation.dto.RouteDeviationResponse;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.telemetry.dynamo.entity.LightDirectionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.LightDirectionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
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
class RouteDeviationServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private RouteDeviationService routeDeviationService;

    @Mock
    private IoTLightJpaRepository iotLightJpaRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private LightDirectionEventRepository lightDirectionEventRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;

    @Mock
    private CctvGridCellRepository cctvGridCellRepository;

    @Mock
    private SchoolContextService schoolContextService;

    private Floor floor;
    private final UUID lightId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        floor = mock(Floor.class);
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
    }

    private MapNode node(String code, NodeType type) {
        MapNode node = type == NodeType.CUSTOM
                ? MapNode.createCustom(floor, code, code, 0, 0)
                : MapNode.create(floor, code, type, code, 0, 0, false);
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        return node;
    }

    private MapEdge edge(MapNode from, MapNode to) {
        MapEdge edge = MapEdge.create(floor, from, to, 3.0, true);
        ReflectionTestUtils.setField(edge, "id", UUID.randomUUID());
        return edge;
    }

    private IoTLight lightWithGuidance() {
        return lightWithGuidance("LIGHT_001", lightId);
    }

    private IoTLight lightWithGuidance(String code, UUID id) {
        MapNode customNode = node(code, NodeType.CUSTOM);
        IoTLight light = IoTLight.create(code, code, customNode);
        ReflectionTestUtils.setField(light, "id", id);
        MapNode decisionNode = node(code + "_HALLWAY1", NodeType.HALLWAY);
        MapNode leftTarget = node(code + "_HALLWAY2", NodeType.HALLWAY);
        MapNode rightTarget = node(code + "_HALLWAY3", NodeType.HALLWAY);
        light.configureGuidance(decisionNode, edge(decisionNode, leftTarget), edge(decisionNode, rightTarget));
        return light;
    }

    private FloorGridCell gridCell() {
        FloorGridCell cell = FloorGridCell.create(floor, 0, 0, true, 0.1, 0.1);
        ReflectionTestUtils.setField(cell, "id", UUID.randomUUID());
        return cell;
    }

    private Cctv cctv(String code) {
        MapNode customNode = node(code, NodeType.CUSTOM);
        Cctv cctv = Cctv.create(code, code, customNode);
        ReflectionTestUtils.setField(cctv, "id", UUID.randomUUID());
        return cctv;
    }

    private CctvGridCell mapping(Cctv cctv, FloorGridCell cell) {
        return CctvGridCell.create(cctv, cell);
    }

    private ObservationItem observation(String cctvCode, double avgHeadcount, long capturedAt) {
        return ObservationItem.create(
                UUID.randomUUID(), sessionId, null, cctvCode,
                avgHeadcount, (int) avgHeadcount, 1, 1.0,
                CongestionLevel.NORMAL, capturedAt - 5_000, capturedAt, capturedAt, null, 1L
        );
    }

    private LightDirectionEventItem directionEvent(IoTLight light, IoTLightDirection direction, long changedAt) {
        return LightDirectionEventItem.create(
                sessionId, light.getId(), light.getCode(), direction,
                light.getDecisionNode().getId(), light.getLeftEdge().getId(), light.getRightEdge().getId(), changedAt
        );
    }

    @Test
    @DisplayName("유도등이 안내한 방향과 다른 쪽 CCTV에서 인원이 탐지되면 이탈로 집계한다")
    void calculate_success() {
        // given
        IoTLight light = lightWithGuidance();
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        FloorGridCell leftCell = gridCell();
        FloorGridCell rightCell = gridCell();
        Cctv leftCctv = cctv("CCTV_LEFT");
        Cctv rightCctv = cctv("CCTV_RIGHT");

        given(iotLightJpaRepository.findByIdAndCustomNode_Floor_Building_SchoolName(lightId, SCHOOL_NAME))
                .willReturn(Optional.of(light));
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));

        given(mapEdgeGridCellRepository.findAllByMapEdge_Id(light.getLeftEdge().getId()))
                .willReturn(List.of(MapEdgeGridCell.create(light.getLeftEdge(), leftCell)));
        given(mapEdgeGridCellRepository.findAllByMapEdge_Id(light.getRightEdge().getId()))
                .willReturn(List.of(MapEdgeGridCell.create(light.getRightEdge(), rightCell)));
        given(cctvGridCellRepository.findAllByGridCell_IdIn(List.of(leftCell.getId())))
                .willReturn(List.of(mapping(leftCctv, leftCell)));
        given(cctvGridCellRepository.findAllByGridCell_IdIn(List.of(rightCell.getId())))
                .willReturn(List.of(mapping(rightCctv, rightCell)));

        given(lightDirectionEventRepository.findAllBySessionIdAndLightCode(sessionId.toString(), light.getCode()))
                .willReturn(List.of(directionEvent(light, IoTLightDirection.LEFT, 1_000L)));

        given(observationRepository.findAllBySessionIdAndCctvCode(anyString(), anyString(), any(Integer.class)))
                .willAnswer(invocation -> {
                    String cctvCode = invocation.getArgument(1);
                    if (cctvCode.equals("CCTV_LEFT")) {
                        return List.of(observation("CCTV_LEFT", 3.0, 2_000L));
                    }
                    return List.of(observation("CCTV_RIGHT", 2.0, 3_000L));
                });

        // when
        RouteDeviationResponse response = routeDeviationService.calculate(lightId, sessionId, EMAIL);

        // then
        assertThat(response.totalObservedWindows()).isEqualTo(2);
        assertThat(response.deviatedWindows()).isEqualTo(1);
        assertThat(response.deviationRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("같은 관측 구간에서 좌/우 CCTV가 모두 탐지되면 한 구간으로만 집계하고 이탈로 판정한다")
    void calculate_bothSidesDetectedInSameWindow_countsOnce() {
        // given
        IoTLight light = lightWithGuidance();
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        FloorGridCell leftCell = gridCell();
        FloorGridCell rightCell = gridCell();
        Cctv leftCctv = cctv("CCTV_LEFT");
        Cctv rightCctv = cctv("CCTV_RIGHT");

        given(iotLightJpaRepository.findByIdAndCustomNode_Floor_Building_SchoolName(lightId, SCHOOL_NAME))
                .willReturn(Optional.of(light));
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));

        given(mapEdgeGridCellRepository.findAllByMapEdge_Id(light.getLeftEdge().getId()))
                .willReturn(List.of(MapEdgeGridCell.create(light.getLeftEdge(), leftCell)));
        given(mapEdgeGridCellRepository.findAllByMapEdge_Id(light.getRightEdge().getId()))
                .willReturn(List.of(MapEdgeGridCell.create(light.getRightEdge(), rightCell)));
        given(cctvGridCellRepository.findAllByGridCell_IdIn(List.of(leftCell.getId())))
                .willReturn(List.of(mapping(leftCctv, leftCell)));
        given(cctvGridCellRepository.findAllByGridCell_IdIn(List.of(rightCell.getId())))
                .willReturn(List.of(mapping(rightCctv, rightCell)));

        given(lightDirectionEventRepository.findAllBySessionIdAndLightCode(sessionId.toString(), light.getCode()))
                .willReturn(List.of(directionEvent(light, IoTLightDirection.LEFT, 1_000L)));

        // 두 CCTV 모두 같은 5초 구간(capturedAt=7000 -> windowStart=2000)에 인원을 탐지한다.
        given(observationRepository.findAllBySessionIdAndCctvCode(anyString(), anyString(), any(Integer.class)))
                .willAnswer(invocation -> {
                    String cctvCode = invocation.getArgument(1);
                    if (cctvCode.equals("CCTV_LEFT")) {
                        return List.of(observation("CCTV_LEFT", 3.0, 7_000L));
                    }
                    return List.of(observation("CCTV_RIGHT", 2.0, 7_000L));
                });

        // when
        RouteDeviationResponse response = routeDeviationService.calculate(lightId, sessionId, EMAIL);

        // then: 두 레코드가 같은 구간이므로 total은 1로만 집계되고, 반대쪽(RIGHT)도 탐지됐으므로 이탈로 판정한다.
        assertThat(response.totalObservedWindows()).isEqualTo(1);
        assertThat(response.deviatedWindows()).isEqualTo(1);
        assertThat(response.deviationRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("경로 안내가 설정되지 않은 유도등이면 예외가 발생한다")
    void calculate_guidanceNotConfigured_throws() {
        MapNode customNode = node("LIGHT_001", NodeType.CUSTOM);
        IoTLight light = IoTLight.create("LIGHT_001", "LIGHT_001", customNode);
        ReflectionTestUtils.setField(light, "id", lightId);
        TrainingSession session = mock(TrainingSession.class);

        given(iotLightJpaRepository.findByIdAndCustomNode_Floor_Building_SchoolName(lightId, SCHOOL_NAME))
                .willReturn(Optional.of(light));
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));

        assertThatThrownBy(() -> routeDeviationService.calculate(lightId, sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.GUIDANCE_NOT_CONFIGURED.getMessage());
    }

    @Test
    @DisplayName("좌/우를 감시하는 CCTV 매핑이 없으면 예외가 발생한다")
    void calculate_noCctvMapping_throws() {
        IoTLight light = lightWithGuidance();
        TrainingSession session = mock(TrainingSession.class);

        given(iotLightJpaRepository.findByIdAndCustomNode_Floor_Building_SchoolName(lightId, SCHOOL_NAME))
                .willReturn(Optional.of(light));
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(mapEdgeGridCellRepository.findAllByMapEdge_Id(any())).willReturn(List.of());

        assertThatThrownBy(() -> routeDeviationService.calculate(lightId, sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.DEVIATION_CCTV_MAPPING_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 훈련 세션이면 예외가 발생한다")
    void calculate_sessionNotFound_throws() {
        IoTLight light = lightWithGuidance();

        given(iotLightJpaRepository.findByIdAndCustomNode_Floor_Building_SchoolName(lightId, SCHOOL_NAME))
                .willReturn(Optional.of(light));
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> routeDeviationService.calculate(lightId, sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasMessage(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND.getMessage());
    }

    // === calculateForSession (훈련 리포트용 세션 단위 집계) ===

    @Test
    @DisplayName("세션이 속한 건물의 모든 유도등 결과를 합산한다")
    void calculateForSession_aggregatesAcrossAllLightsInBuilding() {
        UUID buildingId = UUID.randomUUID();
        IoTLight lightA = lightWithGuidance("LIGHT_A", UUID.randomUUID());
        IoTLight lightB = lightWithGuidance("LIGHT_B", UUID.randomUUID());

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getBuildingId()).willReturn(buildingId);
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getScenario()).willReturn(scenario);

        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(iotLightJpaRepository.findAllByCustomNode_Floor_Building_Id(buildingId))
                .willReturn(List.of(lightA, lightB));

        for (IoTLight light : List.of(lightA, lightB)) {
            FloorGridCell leftCell = gridCell();
            FloorGridCell rightCell = gridCell();
            Cctv leftCctv = cctv(light.getCode() + "_LEFT");
            Cctv rightCctv = cctv(light.getCode() + "_RIGHT");

            given(mapEdgeGridCellRepository.findAllByMapEdge_Id(light.getLeftEdge().getId()))
                    .willReturn(List.of(MapEdgeGridCell.create(light.getLeftEdge(), leftCell)));
            given(mapEdgeGridCellRepository.findAllByMapEdge_Id(light.getRightEdge().getId()))
                    .willReturn(List.of(MapEdgeGridCell.create(light.getRightEdge(), rightCell)));
            given(cctvGridCellRepository.findAllByGridCell_IdIn(List.of(leftCell.getId())))
                    .willReturn(List.of(mapping(leftCctv, leftCell)));
            given(cctvGridCellRepository.findAllByGridCell_IdIn(List.of(rightCell.getId())))
                    .willReturn(List.of(mapping(rightCctv, rightCell)));
            given(lightDirectionEventRepository.findAllBySessionIdAndLightCode(sessionId.toString(), light.getCode()))
                    .willReturn(List.of(directionEvent(light, IoTLightDirection.LEFT, 1_000L)));

            // 좌/우 CCTV가 같은 5초 구간(capturedAt=7000)에 함께 탐지 -> 유도등마다 구간 1개, 이탈 1개
            given(observationRepository.findAllBySessionIdAndCctvCode(
                    eq(sessionId.toString()), eq(light.getCode() + "_LEFT"), any(Integer.class)))
                    .willReturn(List.of(observation(light.getCode() + "_LEFT", 3.0, 7_000L)));
            given(observationRepository.findAllBySessionIdAndCctvCode(
                    eq(sessionId.toString()), eq(light.getCode() + "_RIGHT"), any(Integer.class)))
                    .willReturn(List.of(observation(light.getCode() + "_RIGHT", 2.0, 7_000L)));
        }

        SessionDeviationResult result = routeDeviationService.calculateForSession(sessionId, EMAIL);

        assertThat(result.totalObservedWindows()).isEqualTo(2); // 유도등 2개 x 1구간
        assertThat(result.deviatedWindows()).isEqualTo(2); // 둘 다 반대쪽에서도 탐지되어 이탈
        assertThat(result.deviationRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("경로 설정이 안 된 유도등은 예외 없이 집계에서 제외한다")
    void calculateForSession_skipsUnconfiguredLights() {
        UUID buildingId = UUID.randomUUID();
        MapNode customNode = node("LIGHT_UNCONFIGURED", NodeType.CUSTOM);
        IoTLight unconfigured = IoTLight.create("LIGHT_UNCONFIGURED", "LIGHT_UNCONFIGURED", customNode);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getBuildingId()).willReturn(buildingId);
        TrainingSession session = mock(TrainingSession.class);
        given(session.getScenario()).willReturn(scenario);

        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(iotLightJpaRepository.findAllByCustomNode_Floor_Building_Id(buildingId))
                .willReturn(List.of(unconfigured));

        SessionDeviationResult result = routeDeviationService.calculateForSession(sessionId, EMAIL);

        assertThat(result.totalObservedWindows()).isZero();
        assertThat(result.deviatedWindows()).isZero();
        assertThat(result.deviationRate()).isEqualTo(0.0);
        // isGuidanceConfigured()에서 걸러지므로 computeWindowStats까지 가지 않고, CCTV 조회는 아예 일어나지 않는다.
        org.mockito.Mockito.verifyNoInteractions(mapEdgeGridCellRepository, cctvGridCellRepository);
    }

    @Test
    @DisplayName("경로 설정은 됐지만 좌/우를 감시하는 CCTV 매핑이 없는 유도등은 예외 없이 집계에서 제외한다")
    void calculateForSession_skipsConfiguredLightWithoutCctvMapping() {
        UUID buildingId = UUID.randomUUID();
        IoTLight configuredButUnmapped = lightWithGuidance("LIGHT_NO_MAPPING", UUID.randomUUID());

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getBuildingId()).willReturn(buildingId);
        TrainingSession session = mock(TrainingSession.class);
        given(session.getScenario()).willReturn(scenario);

        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(iotLightJpaRepository.findAllByCustomNode_Floor_Building_Id(buildingId))
                .willReturn(List.of(configuredButUnmapped));
        // 경로 설정(configureGuidance)은 되어 있지만, 그 통로를 감시하는 CCTV 매핑이 하나도 없다.
        given(mapEdgeGridCellRepository.findAllByMapEdge_Id(any())).willReturn(List.of());

        SessionDeviationResult result = routeDeviationService.calculateForSession(sessionId, EMAIL);

        assertThat(result.totalObservedWindows()).isZero();
        assertThat(result.deviatedWindows()).isZero();
        assertThat(result.deviationRate()).isEqualTo(0.0);
    }
}
