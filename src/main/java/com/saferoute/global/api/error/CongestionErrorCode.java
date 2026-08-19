package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CongestionErrorCode implements BaseErrorCode {

    EVENT_PROCESSING_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "CONGESTION001",
            "혼잡 관측 후속 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."
    ),
    EVENT_IDENTITY_MISMATCH(
            HttpStatus.CONFLICT,
            "CONGESTION002",
            "동일한 eventId에 다른 세션, CCTV 또는 경로 정보가 전달되었습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
