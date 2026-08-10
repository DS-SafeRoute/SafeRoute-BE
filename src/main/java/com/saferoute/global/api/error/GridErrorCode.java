package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GridErrorCode implements BaseErrorCode {
    FLOOR_NOT_READY_FOR_GRID(HttpStatus.BAD_REQUEST, "GRID001", "도면 분석(realWidth/realHeight)이 완료되지 않아 그리드를 생성할 수 없습니다."),
    INVALID_CELL_SIZE(HttpStatus.BAD_REQUEST, "GRID002", "그리드 셀 크기는 0보다 커야 합니다."),
    CELL_SIZE_TOO_LARGE(HttpStatus.BAD_REQUEST, "GRID003", "셀 크기가 도면 전체 크기보다 큽니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}