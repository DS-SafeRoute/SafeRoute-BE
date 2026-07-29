package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BuildingErrorCode implements BaseErrorCode {

    BUILDING_NOT_FOUND(HttpStatus.NOT_FOUND, "BUILDING001", "건물을 찾을 수 없습니다."),
    BUILDING_HAS_TRAINING_HISTORY(HttpStatus.BAD_REQUEST, "BUILDING002", "이 건물에는 훈련 기록이 있어서 삭제할 수 없습니다.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}