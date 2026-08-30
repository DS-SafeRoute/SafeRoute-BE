package com.saferoute.domain.device.dto.response;

import com.saferoute.domain.device.entity.LightCommandStatus;
import java.util.UUID;

public record LightCommandAckResponse(
        UUID commandId,
        LightCommandStatus status
) {}
