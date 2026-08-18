package com.saferoute.domain.user.dto;

import com.saferoute.domain.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 요청
public record SignupRequest(
        @NotBlank @Size(min = 2, max = 20) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Email @NotBlank String email,
        @Pattern(regexp = "^$|^[0-9-]{9,20}$") String phoneNumber,
        @NotBlank @Size(min = 5, max = 20) String schoolName,
        UserRole role
) {}
