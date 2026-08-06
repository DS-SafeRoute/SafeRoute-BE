package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.device.entity.IoTLightDirection;
import java.time.Instant;
import java.util.UUID;

public record IoTLightStatusEventData(
        UUID lightId,
        IoTLightDirection direction,
        Instant updatedAt
) {}
