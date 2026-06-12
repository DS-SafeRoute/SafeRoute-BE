package com.saferoute.domain.user.dto;

import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record SignupResponse(
        UUID id,
        String username,
        String email,
        UserRole role,
        String schoolName,
        Instant createdAt
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getSchoolName(),
                user.getCreatedAt()
        );
    }
}