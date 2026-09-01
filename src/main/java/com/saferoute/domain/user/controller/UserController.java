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
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(
            summary = "회원가입",
            description = """
                    새 계정을 생성하고 생성된 사용자 정보를 201 Created로 반환합니다.
                    비밀번호는 해시되어 저장되며 응답에는 포함되지 않습니다.

                    인증 없이 호출할 수 있는 공개 API입니다.

                    role을 생략하면 기본값 NORMAL로 가입되며, MANAGER로 가입하려면
                    role 필드에 "MANAGER"를 명시해야 합니다. email과 username은 각각
                    전체 사용자 중 유일해야 하고, 이미 사용 중이면 각각
                    DUPLICATE_EMAIL / DUPLICATE_USERNAME 오류가 발생합니다.

                    schoolName은 5~20자 필수값이며, 가입 이후에는 다른 학교 이름으로
                    변경할 수 없습니다(내 프로필 수정 API 설명 참고). phoneNumber는
                    선택값이며 숫자와 '-'만 허용됩니다.
                    """
    )
    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserSuccessCode.SIGNUP_COMPLETED, response));
    }

    // 로그인: JWT 없이 자격 증명만 확인 후 사용자 정보를 반환
    @Operation(
            summary = "로그인",
            description = """
                    email/password로 자격 증명을 확인한 뒤 사용자 프로필 정보와 함께
                    access token, refresh token을 발급합니다.

                    인증 없이 호출할 수 있는 공개 API입니다. 존재하지 않는 이메일과
                    비밀번호 불일치는 구분되지 않고 동일하게 INVALID_CREDENTIAL 오류로
                    응답해, 어떤 이메일이 가입되어 있는지 노출하지 않습니다.

                    accessToken은 tokenType="Bearer"와 함께 반환되며 expiresIn(초) 이후
                    만료됩니다. 이후 요청은 Authorization: Bearer {accessToken} 헤더로
                    인증합니다. refreshToken은 accessToken 만료 시 /api/v1/auth/reissue로
                    재발급받는 데 사용하므로 클라이언트가 안전하게 보관해야 합니다.
                    """
    )
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.LOGIN_COMPLETED, response));
    }

    @Operation(
            summary = "내 프로필 조회",
            description = """
                    Authorization 헤더의 access token으로 식별한 현재 로그인 사용자의
                    프로필 정보를 반환합니다.

                    MANAGER, NORMAL 권한을 가진 로그인 사용자만 호출할 수 있습니다.
                    비밀번호 등 민감 정보는 응답에 포함되지 않습니다.
                    """
    )
    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            Authentication authentication
    ) {
        UserProfileResponse response = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.PROFILE_RETRIEVED, response));
    }

    @Operation(
            summary = "내 프로필 수정",
            description = """
                    현재 로그인 사용자의 프로필을 부분 수정합니다. 요청 바디에서 값이
                    채워진 필드만 변경되고, null인 필드는 기존 값이 유지됩니다.

                    username, email은 다른 사용자와 중복될 수 없으며, 중복 시 각각
                    DUPLICATE_USERNAME / DUPLICATE_EMAIL 오류가 발생합니다.

                    schoolName은 현재 소속 학교와 동일한 값을 다시 보내는 경우에만
                    허용됩니다. 다른 값으로 바꾸려고 하면 403 FORBIDDEN이 발생하므로,
                    이 API로는 소속 학교를 변경할 수 없습니다. 비밀번호는 이 API의
                    요청 필드에 없어 변경할 수 없습니다.

                    성공 시 수정이 반영된 최신 프로필 전체를 반환합니다.
                    """
    )
    @PatchMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        UserProfileResponse response = userService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.PROFILE_UPDATED, response));
    }

    // 토큰 재발급: refresh token을 검증하고 새 access/refresh token을 발급한다.
    @Operation(
            summary = "토큰 재발급",
            description = """
                    refreshToken을 검증하고 새 access token, refresh token 쌍을
                    발급합니다.

                    refresh token은 재발급마다 회전(rotate)됩니다. 요청에 사용한
                    refreshToken은 성공 즉시 서버에서 소비되어 다시 사용할 수 없으므로,
                    응답으로 받은 새 refreshToken으로 반드시 교체해서 저장해야 합니다.
                    같은 토큰으로 다시 요청하면 INVALID_REFRESH_TOKEN 오류가 발생합니다.

                    토큰이 만료되었거나 서명이 유효하지 않거나 이미 소비된 경우 모두
                    동일하게 INVALID_REFRESH_TOKEN으로 응답합니다. Authorization
                    헤더 없이 호출할 수 있는 공개 API입니다.
                    """
    )
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

    @Operation(
            summary = "로그아웃",
            description = """
                    Authorization 헤더의 access token을 서버 측 폐기(revocation)
                    목록에 등록해, 원래 만료 시각까지 더 이상 사용할 수 없게 만듭니다.

                    이미 만료된 access token은 폐기 목록에 추가하지 않고 그대로
                    성공으로 처리합니다.

                    refreshToken은 이 API로 무효화되지 않으므로, 완전히 로그아웃하려면
                    클라이언트가 보관 중인 refreshToken도 함께 폐기(삭제)해야 합니다.

                    MANAGER, NORMAL 권한을 가진 로그인 사용자만 호출할 수 있습니다.
                    """
    )
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        String accessToken = authorization.substring("Bearer ".length()).trim();
        accessTokenRevocationService.revoke(accessToken);
        return ResponseEntity.ok(ApiResponse.success(UserSuccessCode.LOGOUT_COMPLETED, null));
    }
}
