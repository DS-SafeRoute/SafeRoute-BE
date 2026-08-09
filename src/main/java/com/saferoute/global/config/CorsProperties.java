package com.saferoute.global.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REST API와 WebSocket handshake의 허용 Origin 설정.
 * 환경별 값은 app.cors.allowed-origins로 주입하며 최소 하나의 Origin이 필요하다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins에는 최소 하나의 유효한 Origin이 필요합니다."
            );
        }

        allowedOrigins.forEach(CorsProperties::validateOrigin);
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    private static void validateOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            throw invalidOrigin(origin);
        }

        if ("*".equals(origin)) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins에는 wildcard(*)를 사용할 수 없습니다."
            );
        }

        try {
            URI uri = new URI(origin);
            boolean validScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            boolean validPort = uri.getPort() == -1
                    || (uri.getPort() >= 1 && uri.getPort() <= 65535);
            boolean hasOnlyOriginComponents = uri.getRawUserInfo() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;

            if (!validScheme || uri.getHost() == null || !validPort || !hasOnlyOriginComponents) {
                throw invalidOrigin(origin);
            }
        } catch (URISyntaxException exception) {
            throw invalidOrigin(origin, exception);
        }
    }

    private static IllegalStateException invalidOrigin(String origin) {
        return new IllegalStateException(
                "유효하지 않은 CORS Origin입니다: " + origin
                        + ". http(s)://host[:port] 형식만 사용할 수 있습니다."
        );
    }

    private static IllegalStateException invalidOrigin(String origin, Exception cause) {
        return new IllegalStateException(
                "유효하지 않은 CORS Origin입니다: " + origin
                        + ". http(s)://host[:port] 형식만 사용할 수 있습니다.",
                cause
        );
    }
}