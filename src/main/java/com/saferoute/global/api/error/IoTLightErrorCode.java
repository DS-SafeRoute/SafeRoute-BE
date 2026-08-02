package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IoTLightErrorCode implements BaseErrorCode {

    IOT_LIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "IOTLIGHT001", "유도등을 찾을 수 없습니다."),
    DUPLICATE_LIGHT_CODE(HttpStatus.CONFLICT, "IOTLIGHT002", "이미 등록된 유도등 코드입니다."),
    DECISION_NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "IOTLIGHT003", "분기 노드를 찾을 수 없습니다."),
    GUIDANCE_EDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IOTLIGHT004", "안내 통로(엣지)를 찾을 수 없습니다."),
    INVALID_GUIDANCE_EDGE(HttpStatus.BAD_REQUEST, "IOTLIGHT005", "leftEdge/rightEdge는 decisionNode에 연결된 엣지여야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
