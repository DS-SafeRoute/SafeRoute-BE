package com.saferoute.domain.auth.controller;

import com.saferoute.domain.auth.dto.LoginRequest;
import com.saferoute.domain.auth.dto.LoginResponse;
import com.saferoute.domain.auth.dto.SignupRequest;
import com.saferoute.domain.auth.dto.SignupResponse;
import com.saferoute.domain.auth.service.AuthService;
import com.saferoute.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로그인/회원가입", description = "회원가입 및 로그인 API (JWT 미적용)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입
    @Operation(summary = "회원가입", description = "이메일/아이디 중복 검사 후 비밀번호를 암호화하여 저장합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입에 성공했습니다.", response));
    }

    // 로그인: JWT 없이 자격 증명만 확인 후 사용자 정보를 반환
    @Operation(summary = "로그인", description = "이메일/비밀번호 확인 후 사용자 정보를 반환합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인에 성공했습니다.", response));
    }
}