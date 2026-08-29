package com.saferoute.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET =
            "c2FmZXJvdXRlLXRlc3Qtand0LXNlY3JldC1rZXktMzItYnl0ZXM=";

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider(
                    new JwtProperties(
                            SECRET,
                            "saferoute",
                            Duration.ofHours(1),
                            Duration.ofDays(14)
                    )
            );

    @Test
    @DisplayName("발급한 access token에서 사용자 이메일을 읽을 수 있다")
    void createAndParseAccessToken() {
        User user = mock(User.class);

        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getEmail())
                .willReturn("manager@saferoute.com");
        given(user.getRole())
                .willReturn(UserRole.MANAGER);

        String token =
                jwtTokenProvider.createAccessToken(user);

        assertThat(jwtTokenProvider.getEmail(token))
                .isEqualTo("manager@saferoute.com");

        assertThat(
                jwtTokenProvider
                        .getAccessTokenExpirationSeconds()
        ).isEqualTo(3600);
    }

    @Test
    @DisplayName("서명이 변조된 access token은 거부한다")
    void rejectTamperedToken() {
        User user = mock(User.class);

        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getEmail())
                .willReturn("normal@saferoute.com");
        given(user.getRole())
                .willReturn(UserRole.NORMAL);

        String token =
                jwtTokenProvider.createAccessToken(user);

        String[] parts = token.split("\\.");

        parts[2] =
                (parts[2].startsWith("a") ? "b" : "a")
                        + parts[2].substring(1);

        String tampered = String.join(".", parts);

        assertThatThrownBy(
                () -> jwtTokenProvider.getEmail(tampered)
        ).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("발급한 refresh token은 type claim이 refresh이고 access token으로는 통과되지 않는다")
    void createAndValidateRefreshToken() {
        User user = mock(User.class);

        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getEmail())
                .willReturn("normal@saferoute.com");
        given(user.getRole())
                .willReturn(UserRole.NORMAL);

        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        String accessToken = jwtTokenProvider.createAccessToken(user);

        jwtTokenProvider.validateRefreshToken(refreshToken);

        assertThatThrownBy(
                () -> jwtTokenProvider.validateRefreshToken(accessToken)
        ).isInstanceOf(JwtException.class);

        assertThat(
                jwtTokenProvider.getRefreshTokenExpirationSeconds()
        ).isEqualTo(Duration.ofDays(14).toSeconds());
    }

    @Test
    @DisplayName("JWT secret이 비어 있으면 시작 단계에서 실패한다")
    void rejectBlankSecret() {
        JwtProperties properties =
                new JwtProperties(
                        "",
                        "saferoute",
                        Duration.ofHours(1),
                        Duration.ofDays(14)
                );

        assertThatThrownBy(
                () -> new JwtTokenProvider(properties)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}