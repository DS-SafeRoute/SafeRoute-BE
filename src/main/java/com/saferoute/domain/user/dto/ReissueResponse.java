package com.saferoute.domain.user.dto;

public record ReissueResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken
) {

    public static ReissueResponse of(
            String accessToken,
            long expiresIn,
            String refreshToken
    ) {
        return new ReissueResponse("Bearer", accessToken, expiresIn, refreshToken);
    }
}
