package com.saferoute.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    @DisplayName("여러 CORS Origin을 순서대로 보관한다")
    void keepsMultipleAllowedOrigins() {
        CorsProperties properties = new CorsProperties(List.of(
                "http://localhost:3000",
                "https://ds-saferoute.site"
        ));

        assertEquals(
                List.of(
                        "http://localhost:3000",
                        "https://ds-saferoute.site"
                ),
                properties.allowedOrigins()
        );
    }

    @Test
    @DisplayName("CORS Origin 목록이 비어 있으면 설정 오류로 처리한다")
    void rejectsEmptyAllowedOrigins() {
        assertThrows(
                IllegalStateException.class,
                () -> new CorsProperties(List.of())
        );
    }

    @Test
    @DisplayName("빈 문자열 Origin이 포함되면 설정 오류로 처리한다")
    void rejectsBlankAllowedOrigin() {
        assertThrows(
                IllegalStateException.class,
                () -> new CorsProperties(
                        List.of(
                                "http://localhost:3000",
                                " "
                        )
                )
        );
    }
}