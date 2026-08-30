package com.saferoute.domain.device.dto.response;

import java.util.List;

public record LightCommandListResponse(
        List<LightCommandResponse> commands
) {}
