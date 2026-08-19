package com.saferoute.global.security;

import java.util.UUID;

public record DevicePrincipal(
        UUID cctvId,
        String cctvCode
) {
}
