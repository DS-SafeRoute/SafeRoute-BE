package com.saferoute.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
    public CorsProperties {
        if (allowedOrigins == null
                || allowedOrigins.isEmpty()
                || allowedOrigins.stream()
                .anyMatch(origin -> origin == null || origin.isBlank())) {

            throw new IllegalStateException(
                    "app.cors.allowed-origins에는 최소 하나의 유효한 Origin이 필요합니다."
            );
        }

        allowedOrigins = List.copyOf(allowedOrigins);
    }
}