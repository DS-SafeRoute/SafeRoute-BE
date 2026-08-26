package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IoTLightErrorCode implements BaseErrorCode {

    IOT_LIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "IOTLIGHT001", "유도등을 찾을 수 없습니다."),
    DECISION_NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "IOTLIGHT003", "분기 노드를 찾을 수 없습니다."),
    GUIDANCE_EDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IOTLIGHT004", "안내 통로(엣지)를 찾을 수 없습니다."),
    INVALID_GUIDANCE_EDGE(HttpStatus.BAD_REQUEST, "IOTLIGHT005", "leftEdge/rightEdge는 decisionNode에 연결된 엣지여야 합니다."),
    GUIDANCE_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "IOTLIGHT006", "경로 안내(좌/우 통로)가 설정되지 않은 유도등입니다."),
    LIGHT_DISABLED(HttpStatus.BAD_REQUEST, "IOTLIGHT007", "비활성화된 유도등에는 명령을 보낼 수 없습니다."),
    DEVICE_UNREACHABLE(HttpStatus.BAD_GATEWAY, "IOTLIGHT008", "라즈베리파이 기기와 통신에 실패했습니다."),
    DEVIATION_CCTV_MAPPING_NOT_FOUND(HttpStatus.BAD_REQUEST, "IOTLIGHT009",
            "경로 이탈률을 계산하려면 좌/우 통로를 각각 감시하는 CCTV가 매핑되어 있어야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
