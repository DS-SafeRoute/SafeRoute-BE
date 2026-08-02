package com.saferoute.global.security;

import com.saferoute.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        validateProperties(properties);
        this.properties = properties;
        this.signingKey = createSigningKey(properties.secret());
    }

    public String createAccessToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String getEmail(String token) {
        String email = parseClaims(token).get("email", String.class);

        if (email == null || email.isBlank()) {
            throw new JwtException("JWT에 email claim이 없습니다.");
        }

        return email;
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpiration().toSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey createSigningKey(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 환경변수가 필요합니다.");
        }

        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodedSecret));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET은 Base64 인코딩된 256비트 이상의 키여야 합니다.",
                    exception
            );
        }
    }

    private void validateProperties(JwtProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("JWT 설정이 필요합니다.");
        }

        if (properties.issuer() == null || properties.issuer().isBlank()) {
            throw new IllegalStateException("JWT issuer 설정이 필요합니다.");
        }

        if (properties.accessTokenExpiration() == null
                || properties.accessTokenExpiration().isZero()
                || properties.accessTokenExpiration().isNegative()) {
            throw new IllegalStateException(
                    "JWT access token 만료 시간은 0보다 커야 합니다."
            );
        }
    }
}