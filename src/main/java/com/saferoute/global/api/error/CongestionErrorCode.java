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
    ),
    EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CONGESTION003",
            "혼잡 이벤트를 찾을 수 없습니다."
    ),
    EVENT_NOT_PROCESSED(
            HttpStatus.CONFLICT,
            "CONGESTION004",
            "처리가 완료된 혼잡 이벤트에만 이미지를 연결할 수 있습니다."
    ),
    EVENT_IMAGE_STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "CONGESTION005",
            "현재 이미지 상태에서는 이미지를 연결할 수 없습니다."
    ),
    EVENT_IMAGE_KEY_INVALID(
            HttpStatus.BAD_REQUEST,
            "CONGESTION006",
            "이벤트 이미지 경로 형식이 올바르지 않습니다."
    ),
    EVENT_IMAGE_IDENTITY_MISMATCH(
            HttpStatus.CONFLICT,
            "CONGESTION007",
            "이미지 경로의 세션, CCTV 또는 eventId가 이벤트와 일치하지 않습니다."
    ),
    EVENT_IMAGE_OBJECT_NOT_FOUND(
            HttpStatus.CONFLICT,
            "CONGESTION008",
            "업로드가 완료된 이벤트 이미지 객체를 찾을 수 없습니다."
    ),
    MONITORED_AREA_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "CONGESTION009",
            "CCTV의 감시 면적을 계산할 수 없습니다. 감시 영역이 설정되지 않았습니다."
    ),
    MONITORING_IMAGE_KEY_INVALID(
            HttpStatus.BAD_REQUEST,
            "CONGESTION010",
            "모니터링 이미지 경로 형식이 올바르지 않습니다."
    ),
    MONITORING_IMAGE_IDENTITY_MISMATCH(
            HttpStatus.CONFLICT,
            "CONGESTION011",
            "모니터링 이미지 경로의 세션, CCTV 또는 캡처 시각이 요청과 일치하지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
