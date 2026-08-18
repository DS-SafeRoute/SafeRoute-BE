package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrainingErrorCode implements BaseErrorCode {

    TRAINING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRAINING001", "훈련 세션을 찾을 수 없습니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "TRAINING002", "관리자를 찾을 수 없습니다."),
    TRAINING_SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "TRAINING003", "훈련 시나리오를 찾을 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "TRAINING004", "현재 상태에서는 요청한 전이를 수행할 수 없습니다."),
    UNSUPPORTED_STATUS(HttpStatus.CONFLICT, "TRAINING005", "지원하지 않는 훈련 상태입니다."),
    RUNNING_TRAINING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND,"TRAINING006", "진행 중인 훈련 세션을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
