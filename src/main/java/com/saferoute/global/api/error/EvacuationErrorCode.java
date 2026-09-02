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
    EVACUATION_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVAC005", "도달 가능한 EXIT 노드가 없습니다."),
    EXIT_NODE_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "EVAC006", "마지막 출구 노드는 삭제할 수 없습니다."),
    EXIT_NODE_NOT_DESIGNATED(HttpStatus.NOT_FOUND, "EVAC007", "지정된 출구 노드가 없습니다."),
    ROUTE_RECALCULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "EVAC008", "재탐색 요청을 찾을 수 없습니다."),
    INVALID_RECALCULATION_STATUS_TRANSITION(HttpStatus.CONFLICT, "EVAC009", "이미 처리된 재탐색 요청입니다."),
    EXIT_NODE_UNSET_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "EVAC010", "마지막 출구 노드는 EXIT 대상에서 해제할 수 없습니다."),
    FLOOR_START_NODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EVAC011", "해당 층에는 이미 대표 대피 시작 노드가 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
