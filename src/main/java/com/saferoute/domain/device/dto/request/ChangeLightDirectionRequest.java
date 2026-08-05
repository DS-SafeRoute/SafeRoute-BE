package com.saferoute.domain.device.dto.request;

import com.saferoute.domain.device.entity.IoTLightDirection;
import jakarta.validation.constraints.NotNull;

public record ChangeLightDirectionRequest(
        @NotNull IoTLightDirection direction
) {}
