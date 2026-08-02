package com.saferoute.domain.device.service;

import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.entity.IoTLight;
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

    @Transactional
    public IoTLightResponse createLight(CreateIoTLightRequest request) {
        Floor floor = floorRepository.findById(request.floorId())
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        if (iotLightJpaRepository.existsByCode(request.code())) {
            throw new ApiException(IoTLightErrorCode.DUPLICATE_LIGHT_CODE);
        }

        // customNode의 code/name은 유도등 자체의 code/name을 그대로 사용한다.
        // IoTLight.code는 시스템 전역 유일이 보장되므로 MapNode의 (floor, code) 유니크 제약도 자동 충족된다.
        MapNode customNode = mapNodeJpaRepository.save(
                MapNode.createCustom(floor, request.code(), request.name(), request.x(), request.y())
        );

        IoTLight light = iotLightJpaRepository.save(
                IoTLight.create(request.code(), request.name(), customNode)
        );

        return IoTLightResponse.from(light);
    }

    public List<IoTLightResponse> getLights(UUID floorId) {
        List<IoTLight> lights = floorId != null
                ? iotLightJpaRepository.findAllByCustomNode_Floor_Id(floorId)
                : iotLightJpaRepository.findAll();
        return lights.stream().map(IoTLightResponse::from).toList();
    }

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
