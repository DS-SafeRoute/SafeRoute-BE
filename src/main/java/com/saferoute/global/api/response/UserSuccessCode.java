package com.saferoute.global.api.response;


import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseCode {

    SIGNUP_COMPLETED(HttpStatus.CREATED, "USER_SUCCESS_001", "회원가입에 성공했습니다."),
    LOGIN_COMPLETED(HttpStatus.OK, "USER_SUCCESS_002", "로그인에 성공했습니다."),
    PROFILE_RETRIEVED(HttpStatus.OK, "USER_SUCCESS_003", "사용자 정보 조회에 성공했습니다."),
    PROFILE_UPDATED(HttpStatus.OK, "USER_SUCCESS_004", "사용자 정보 수정에 성공했습니다."),
    LOGOUT_COMPLETED(HttpStatus.OK, "USER_SUCCESS_005", "로그아웃에 성공했습니다."),
    REISSUE_COMPLETED(HttpStatus.OK, "USER_SUCCESS_006", "토큰 재발급에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
