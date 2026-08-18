package com.saferoute.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessTokenRevocationService {

    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void revoke(String accessToken) {
        Instant now = Instant.now();
        revokedAccessTokenRepository.deleteAllByExpiresAtBefore(now);

        Instant expiresAt = jwtTokenProvider.getExpiration(accessToken);
        if (expiresAt.isAfter(now)) {
            revokedAccessTokenRepository.save(
                    RevokedAccessToken.create(hash(accessToken), expiresAt)
            );
        }
    }

    public boolean isRevoked(String accessToken) {
        return revokedAccessTokenRepository.existsById(hash(accessToken));
    }

    private String hash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(accessToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
