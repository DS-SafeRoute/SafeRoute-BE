package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCctvRequest(
        @NotNull UUID cctvId
) {}
