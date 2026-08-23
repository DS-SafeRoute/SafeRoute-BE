package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CongestionSuccessCode implements BaseCode {

    CONGESTION_IMAGE_URL_ISSUED(HttpStatus.OK, "CONGESTION_SUCCESS_001", "이미지 조회 URL이 발급되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
