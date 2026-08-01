package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EvacuationErrorCode implements BaseErrorCode {

    MAP_NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVAC001", "노드를 찾을 수 없습니다."),
    MAP_EDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVAC002", "엣지를 찾을 수 없습니다."),
    DUPLICATE_MAP_EDGE(HttpStatus.CONFLICT, "EVAC003", "이미 연결된 노드 쌍입니다."),
    INVALID_MAP_EDGE_CONNECTION(HttpStatus.BAD_REQUEST, "EVAC004", "ROOM 노드는 DOOR 노드를 통해서만 연결할 수 있습니다."),
    EVACUATION_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVAC005", "도달 가능한 EXIT 노드가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
