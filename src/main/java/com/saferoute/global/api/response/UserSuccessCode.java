package com.saferoute.global.api.response;


import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseCode {

    SIGNUP_COMPLETED(HttpStatus.CREATED, "USER_SUCCESS_001", "회원가입에 성공했습니다."),
    LOGIN_COMPLETED(HttpStatus.OK, "USER_SUCCESS_002", "로그인에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}