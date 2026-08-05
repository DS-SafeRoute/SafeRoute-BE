package com.saferoute.domain.device.service;

import com.saferoute.domain.device.client.IoTLightPiClient;
import com.saferoute.domain.device.dto.request.ChangeLightDirectionRequest;
import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdatePiEndpointRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.dto.response.LightDirectionResponse;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IoTLightService {

    private final IoTLightJpaRepository iotLightJpaRepository;
    private final MapNodeJpaRepository mapNodeJpaRepository;
    private final MapEdgeJpaRepository mapEdgeJpaRepository;
    private final FloorRepository floorRepository;
    private final IoTLightPiClient iotLightPiClient;
    private final IoTLightDirectionStore iotLightDirectionStore;
    private final TrainingEventPublisher trainingEventPublisher;

    @Transactional
    public IoTLightResponse createLight(CreateIoTLightRequest request) {
        Floor floor = floorRepository.findById(request.floorId())
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        String code = generateLightCode();

        // customNode의 code/name은 유도등 자체의 code/name을 그대로 사용한다.
        // IoTLight.code는 시스템 전역 유일이 보장되므로 MapNode의 (floor, code) 유니크 제약도 자동 충족된다.
        MapNode customNode = mapNodeJpaRepository.save(
                MapNode.createCustom(floor, code, request.name(), request.x(), request.y())
        );

        IoTLight light = iotLightJpaRepository.save(
                IoTLight.create(code, request.name(), customNode)
        );

        return IoTLightResponse.from(light);
    }

    // 유도등 code는 기기가 보고하는 시리얼이 아니라 서버가 등록 시점에 채번하는 식별자다 (예: LIGHT_001).
    private String generateLightCode() {
        long seq = iotLightJpaRepository.count() + 1;
        return "LIGHT_%03d".formatted(seq);
    }

    @Transactional(readOnly = true)
    public List<IoTLightResponse> getLights(UUID floorId) {
        List<IoTLight> lights = floorId != null
                ? iotLightJpaRepository.findAllByCustomNode_Floor_Id(floorId)
                : iotLightJpaRepository.findAll();
        return lights.stream().map(IoTLightResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IoTLightResponse getLight(UUID lightId) {
        return IoTLightResponse.from(findLightOrThrow(lightId));
    }

    @Transactional
    public IoTLightResponse configureGuidance(UUID lightId, ConfigureGuidanceRequest request) {
        IoTLight light = findLightOrThrow(lightId);

        // leftEdge/rightEdge가 같은 엣지면 IoTLight.configureGuidance()가 IllegalArgumentException을
        // 던지는데 GlobalExceptionHandler가 이를 500으로 처리하므로 여기서 먼저 ApiException으로 막는다.
        if (request.leftEdgeId().equals(request.rightEdgeId())) {
            throw new ApiException(IoTLightErrorCode.INVALID_GUIDANCE_EDGE);
        }

        MapNode decisionNode = mapNodeJpaRepository.findById(request.decisionNodeId())
                .orElseThrow(() -> new ApiException(IoTLightErrorCode.DECISION_NODE_NOT_FOUND));
        MapEdge leftEdge = mapEdgeJpaRepository.findById(request.leftEdgeId())
                .orElseThrow(() -> new ApiException(IoTLightErrorCode.GUIDANCE_EDGE_NOT_FOUND));
        MapEdge rightEdge = mapEdgeJpaRepository.findById(request.rightEdgeId())
                .orElseThrow(() -> new ApiException(IoTLightErrorCode.GUIDANCE_EDGE_NOT_FOUND));

        validateConnectedToDecisionNode(decisionNode, leftEdge);
        validateConnectedToDecisionNode(decisionNode, rightEdge);

        light.configureGuidance(decisionNode, leftEdge, rightEdge);
        return IoTLightResponse.from(light);
    }

    @Transactional
    public IoTLightResponse updateLight(UUID lightId, UpdateIoTLightRequest request) {
        IoTLight light = findLightOrThrow(lightId);
        light.rename(request.name());
        light.getCustomNode().moveTo(request.x(), request.y());
        return IoTLightResponse.from(light);
    }

    @Transactional
    public IoTLightResponse updatePiEndpoint(UUID lightId, UpdatePiEndpointRequest request) {
        IoTLight light = findLightOrThrow(lightId);
        light.updatePiEndpoint(request.piEndpoint());
        return IoTLightResponse.from(light);
    }

    @Transactional
    public IoTLightResponse enableLight(UUID lightId) {
        IoTLight light = findLightOrThrow(lightId);
        light.enable();
        return IoTLightResponse.from(light);
    }

    @Transactional
    public IoTLightResponse disableLight(UUID lightId) {
        IoTLight light = findLightOrThrow(lightId);
        light.disable();
        return IoTLightResponse.from(light);
    }

    // 검증(enabled/guidance) -> Pi 호출 -> 상태 저장 -> 이벤트 발행 순서로 조립한다.
    // direction은 훈련별 동적 상태라 DB에 저장하지 않고 IoTLightDirectionStore(서버 메모리)에 저장한다 (IoTLight 참고).
    public LightDirectionResponse changeDirection(UUID lightId, ChangeLightDirectionRequest request) {
        IoTLight light = findLightOrThrow(lightId);
        IoTLightDirection direction = request.direction();

        if (!light.isEnabled()) {
            throw new ApiException(IoTLightErrorCode.LIGHT_DISABLED);
        }
        if (direction != IoTLightDirection.OFF && !light.isGuidanceConfigured()) {
            throw new ApiException(IoTLightErrorCode.GUIDANCE_NOT_CONFIGURED);
        }
        if (light.getPiEndpoint() == null || light.getPiEndpoint().isBlank()) {
            throw new ApiException(IoTLightErrorCode.DEVICE_UNREACHABLE);
        }

        iotLightPiClient.sendDirection(light.getPiEndpoint(), light.getCode(), direction);
        iotLightDirectionStore.update(light.getId(), direction);
        trainingEventPublisher.publishIoTLightStatusUpdatedAfterCommit(light, direction);

        return new LightDirectionResponse(light.getId(), direction, Instant.now());
    }

    private IoTLight findLightOrThrow(UUID lightId) {
        return iotLightJpaRepository.findById(lightId)
                .orElseThrow(() -> new ApiException(IoTLightErrorCode.IOT_LIGHT_NOT_FOUND));
    }

    // leftEdge/rightEdge가 실제로 decisionNode에 연결된 엣지인지 검증 (엔티티가 아닌 Service 책임)
    private void validateConnectedToDecisionNode(MapNode decisionNode, MapEdge edge) {
        boolean connected = edge.getFromNode().getId().equals(decisionNode.getId())
                || edge.getToNode().getId().equals(decisionNode.getId());
        if (!connected) {
            throw new ApiException(IoTLightErrorCode.INVALID_GUIDANCE_EDGE);
        }
    }
}
