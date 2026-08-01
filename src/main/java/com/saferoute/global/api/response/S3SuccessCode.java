package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum S3SuccessCode implements BaseCode {

    S3_FILE_UPLOADED(HttpStatus.CREATED,"S3_SUCCESS_001","S3 테스트 파일 업로드에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
