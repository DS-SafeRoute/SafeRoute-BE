package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrainingSuccessCode implements BaseCode {

    TRAINING_SESSION_CREATED(HttpStatus.CREATED, "TRAINING_SUCCESS_001", "훈련 세션이 생성되었습니다."),
    TRAINING_STATUS_FOUND(HttpStatus.OK, "TRAINING_SUCCESS_002", "훈련 상태 조회에 성공했습니다."),
    TRAINING_STARTED(HttpStatus.OK, "TRAINING_SUCCESS_003", "훈련이 시작되었습니다."),
    TRAINING_ENDED(HttpStatus.OK, "TRAINING_SUCCESS_004", "훈련이 정상 종료되었습니다."),
    TRAINING_FORCE_ENDED(HttpStatus.OK, "TRAINING_SUCCESS_005", "훈련이 강제 종료되었습니다."),
    MONITORING_CAMERA_LIST_FOUND(
            HttpStatus.OK,
            "TRAINING_SUCCESS_006",
            "모니터링 카메라 목록 조회에 성공했습니다."
    ),
    MONITORING_FRAME_LIST_FOUND(
            HttpStatus.OK,
            "TRAINING_SUCCESS_007",
            "카메라별 프레임 목록 조회에 성공했습니다."
    ),
    TRAINING_SESSION_LIST_FOUND(
            HttpStatus.OK,
            "TRAINING_SUCCESS_008",
            "훈련 세션 목록 조회에 성공했습니다."
    ),
    MONITORING_EVENT_LIST_FOUND(
            HttpStatus.OK,
            "TRAINING_SUCCESS_009",
            "모니터링 이벤트 타임라인 조회에 성공했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
