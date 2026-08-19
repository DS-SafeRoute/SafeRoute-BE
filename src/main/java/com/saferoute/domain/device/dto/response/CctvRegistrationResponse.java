package com.saferoute.domain.device.dto.response;

public record CctvRegistrationResponse(
        CctvResponse cctv,
        String deviceToken
) {
}
