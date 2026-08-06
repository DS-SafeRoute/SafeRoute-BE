package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePiEndpointRequest(
        @NotBlank String piEndpoint
) {}
