package com.saferoute.global.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    void deleteAllByExpiresAtBefore(Instant expiresAt);

    // 단일 사용(rotation) 보장을 위해 조회 없이 조건부로 원자적 삭제한다.
    // 영향받은 행 수가 1이어야 이 요청이 토큰을 정당하게 소비한 것이다.
    @Modifying
    @Query(
            "delete from RefreshToken t "
                    + "where t.tokenHash = :tokenHash "
                    + "and t.userId = :userId "
                    + "and t.expiresAt > :now"
    )
    int deleteValidToken(
            @Param("tokenHash") String tokenHash,
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );
}
