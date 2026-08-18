package com.saferoute.global.security;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedAccessTokenRepository
        extends JpaRepository<RevokedAccessToken, String> {

    void deleteAllByExpiresAtBefore(Instant expiresAt);
}
