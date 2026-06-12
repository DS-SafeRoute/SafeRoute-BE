package com.saferoute.domain.auth.dto;

import com.saferoute.domain.auth.entity.User;
import com.saferoute.domain.auth.entity.UserRole;
import java.util.UUID;

// 로그인 응답 (JWT 미적용 - 사용자 정보만 반환, 추후 토큰 필드 추가 예정)
public record LoginResponse(
        UUID id,
        String username,
        String email,
        UserRole role,
        String schoolName
) {
    public static LoginResponse from(User user) {
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getSchoolName()
        );
    }
}