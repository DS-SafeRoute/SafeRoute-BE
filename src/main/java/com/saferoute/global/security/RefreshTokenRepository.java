package com.saferoute.global.security;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    void deleteAllByExpiresAtBefore(Instant expiresAt);
}
