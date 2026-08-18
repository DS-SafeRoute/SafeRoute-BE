package com.saferoute.global.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "revoked_access_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevokedAccessToken {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private RevokedAccessToken(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RevokedAccessToken create(String tokenHash, Instant expiresAt) {
        return new RevokedAccessToken(tokenHash, expiresAt);
    }
}
