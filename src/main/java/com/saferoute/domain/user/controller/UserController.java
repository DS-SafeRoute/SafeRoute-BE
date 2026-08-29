package com.saferoute.domain.user.controller;

import com.saferoute.domain.user.dto.LoginRequest;
import com.saferoute.domain.user.dto.LoginResponse;
import com.saferoute.domain.user.dto.ReissueRequest;
import com.saferoute.domain.user.dto.ReissueResponse;
import com.saferoute.domain.user.dto.SignupRequest;
import com.saferoute.domain.user.dto.SignupResponse;
import com.saferoute.domain.user.dto.UpdateUserProfileRequest;
import com.saferoute.domain.user.dto.UserProfileResponse;
import com.saferoute.domain.user.service.UserService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.UserSuccessCode;
import com.saferoute.global.security.AccessTokenRevocationService;
import com.saferoute.global.security.JwtTokenProvider;
import com.saferoute.global.security.RefreshTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "회원가입/로그인 및 사용자 정보 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    // 회원가입
    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserSuccessCode.SIGNUP_COMPLETED, response));
    }

    // 로그인: JWT 없이 자격 증명만 확인 후 사용자 정보를 반환
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.LOGIN_COMPLETED, response));
    }

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            Authentication authentication
    ) {
        UserProfileResponse response = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.PROFILE_RETRIEVED, response));
    }

    @PatchMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        UserProfileResponse response = userService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.PROFILE_UPDATED, response));
    }

    // 토큰 재발급: refresh token을 검증하고 새 access/refresh token을 발급한다.
    @PostMapping("/auth/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        RefreshTokenService.ReissuedTokens tokens = refreshTokenService.reissue(request.refreshToken());
        ReissueResponse response = ReissueResponse.of(
                tokens.accessToken(),
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                tokens.refreshToken()
        );
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.REISSUE_COMPLETED, response));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        String accessToken = authorization.substring("Bearer ".length()).trim();
        accessTokenRevocationService.revoke(accessToken);
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.LOGOUT_COMPLETED, null));
    }
}
