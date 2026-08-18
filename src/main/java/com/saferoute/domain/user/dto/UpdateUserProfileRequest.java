package com.saferoute.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(min = 2, max = 20) String username,
        @Pattern(regexp = "^$|^[0-9-]{9,20}$") String phoneNumber,
        @Email @Size(min = 1, max = 255) String email,
        @Size(min = 5, max = 20) String schoolName
) {}
