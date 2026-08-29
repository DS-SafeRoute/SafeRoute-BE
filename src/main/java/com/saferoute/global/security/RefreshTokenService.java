package com.saferoute.global.security;

import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.error.UserErrorCode;
import com.saferoute.global.api.exception.ApiException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String issue(User user) {
        refreshTokenRepository.deleteAllByExpiresAtBefore(Instant.now());

        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        refreshTokenRepository.save(
                RefreshToken.create(
                        hash(refreshToken),
                        user.getId(),
                        jwtTokenProvider.getExpiration(refreshToken)
                )
        );

        return refreshToken;
    }

    @Transactional
    public ReissuedTokens reissue(String refreshToken) {
        UUID userId;
        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
            userId = jwtTokenProvider.getUserId(refreshToken);
        } catch (JwtException exception) {
            throw new ApiException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenHash = hash(refreshToken);
        RefreshToken stored = refreshTokenRepository.findById(tokenHash)
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(UserErrorCode.INVALID_REFRESH_TOKEN));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.INVALID_REFRESH_TOKEN));

        refreshTokenRepository.delete(stored);

        String newAccessToken = jwtTokenProvider.createAccessToken(user);
        String newRefreshToken = issue(user);

        return new ReissuedTokens(newAccessToken, newRefreshToken);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    public record ReissuedTokens(String accessToken, String refreshToken) {
    }
}
