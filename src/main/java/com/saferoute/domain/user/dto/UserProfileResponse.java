package com.saferoute.domain.user.dto;

import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String phoneNumber,
        String email,
        UserRole role,
        String schoolName
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole(),
                user.getSchoolName()
        );
    }
}
