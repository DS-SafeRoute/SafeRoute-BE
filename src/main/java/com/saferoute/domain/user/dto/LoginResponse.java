package com.saferoute.domain.user.dto;

import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import java.util.UUID;

public record LoginResponse(
        UUID id,
        String username,
        String email,
        String phoneNumber,
        UserRole role,
        String schoolName,
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken
) {

    public static LoginResponse of(
            User user,
            String accessToken,
            long expiresIn,
            String refreshToken
    ) {
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getSchoolName(),
                "Bearer",
                accessToken,
                expiresIn,
                refreshToken
        );
    }
}
