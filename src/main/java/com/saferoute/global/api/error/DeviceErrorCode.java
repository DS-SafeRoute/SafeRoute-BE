package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeviceErrorCode implements BaseErrorCode {

    DEVICE_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "DEVICE001", "디바이스 토큰이 필요합니다."),
    INVALID_DEVICE_TOKEN(HttpStatus.UNAUTHORIZED, "DEVICE002", "유효하지 않은 디바이스 토큰입니다."),
    CCTV_CODE_MISMATCH(HttpStatus.FORBIDDEN, "DEVICE003", "인증된 CCTV와 요청한 CCTV가 일치하지 않습니다."),
    CCTV_DISABLED(HttpStatus.FORBIDDEN, "DEVICE004", "비활성화된 CCTV입니다."),
    DEVICE_TOKEN_ALREADY_ISSUED(HttpStatus.CONFLICT, "DEVICE005", "디바이스 토큰이 이미 발급되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
