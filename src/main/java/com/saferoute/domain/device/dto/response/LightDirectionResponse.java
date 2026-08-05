package com.saferoute.domain.device.dto.response;

import com.saferoute.domain.device.entity.IoTLightDirection;
import java.time.Instant;
import java.util.UUID;

public record LightDirectionResponse(
        UUID lightId,
        IoTLightDirection direction,
        Instant updatedAt
) {}
