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

        // 조회 후 삭제하면 동시 요청이 삭제 전에 모두 조회를 통과해 토큰이 중복 소비될 수 있다.
        // 조건부 DELETE 자체를 소비 판정 기준으로 삼아, 영향받은 행이 정확히 1개일 때만 계속 진행한다.
        int deletedCount = refreshTokenRepository.deleteValidToken(
                hash(refreshToken),
                userId,
                Instant.now()
        );

        if (deletedCount != 1) {
            throw new ApiException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(UserErrorCode.INVALID_REFRESH_TOKEN));

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
