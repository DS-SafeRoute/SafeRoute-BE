package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotNull;

public record AckLightCommandRequest(
        @NotNull Boolean success,
        String failReason
) {}
