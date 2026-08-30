package com.saferoute.domain.device.dto.response;

import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.entity.LightCommand;
import java.util.UUID;

public record LightCommandResponse(
        UUID commandId,
        String lightCode,
        IoTLightDirection direction
) {
    public static LightCommandResponse from(LightCommand command) {
        return new LightCommandResponse(
                command.getId(),
                command.getLight().getCode(),
                command.getDirection()
        );
    }
}
