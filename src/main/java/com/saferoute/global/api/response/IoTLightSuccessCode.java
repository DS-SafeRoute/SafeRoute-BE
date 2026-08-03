package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IoTLightSuccessCode implements BaseCode {

    IOT_LIGHT_CREATED(HttpStatus.CREATED, "IOTLIGHT_SUCCESS_001", "유도등이 등록되었습니다."),
    IOT_LIGHT_LIST_FOUND(HttpStatus.OK, "IOTLIGHT_SUCCESS_002", "유도등 목록 조회에 성공했습니다."),
    IOT_LIGHT_DETAIL_FOUND(HttpStatus.OK, "IOTLIGHT_SUCCESS_003", "유도등 상세 조회에 성공했습니다."),
    IOT_LIGHT_GUIDANCE_CONFIGURED(HttpStatus.OK, "IOTLIGHT_SUCCESS_004", "분기 경로가 설정되었습니다."),
    IOT_LIGHT_UPDATED(HttpStatus.OK, "IOTLIGHT_SUCCESS_005", "유도등 정보가 수정되었습니다."),
    IOT_LIGHT_ENABLED(HttpStatus.OK, "IOTLIGHT_SUCCESS_006", "유도등이 활성화되었습니다."),
    IOT_LIGHT_DISABLED(HttpStatus.OK, "IOTLIGHT_SUCCESS_007", "유도등이 비활성화되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
