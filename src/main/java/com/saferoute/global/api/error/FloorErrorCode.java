package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FloorErrorCode implements BaseErrorCode {

    FLOOR_NOT_FOUND(HttpStatus.NOT_FOUND, "FLOOR001", "도면을 찾을 수 없습니다."),
    DUPLICATE_FLOOR_NUM(HttpStatus.CONFLICT, "FLOOR002", "이미 등록된 층 번호입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}