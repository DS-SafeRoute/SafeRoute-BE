package com.saferoute.domain.device.dto.response;

import com.saferoute.domain.device.entity.IoTLight;
import java.util.UUID;

public record IoTLightResponse(
        UUID id,
        String code,
        String name,
        UUID floorId,
        double x,
        double y,
        UUID decisionNodeId,
        UUID leftEdgeId,
        UUID rightEdgeId,
        boolean guidanceConfigured,
        boolean enabled,
        String piEndpoint
) {
    public static IoTLightResponse from(IoTLight light) {
        return new IoTLightResponse(
                light.getId(),
                light.getCode(),
                light.getName(),
                light.getCustomNode().getFloor().getId(),
                light.getCustomNode().getX(),
                light.getCustomNode().getY(),
                light.getDecisionNode() != null ? light.getDecisionNode().getId() : null,
                light.getLeftEdge() != null ? light.getLeftEdge().getId() : null,
                light.getRightEdge() != null ? light.getRightEdge().getId() : null,
                light.isGuidanceConfigured(),
                light.isEnabled(),
                light.getPiEndpoint()
        );
    }
}
