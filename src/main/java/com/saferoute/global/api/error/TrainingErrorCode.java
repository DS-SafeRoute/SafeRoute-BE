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
    RUNNING_TRAINING_SESSION_NOT_FOUND(HttpStatus.CONFLICT,"TRAINING006", "진행 중인 훈련 세션을 찾을 수 없습니다."),
    SCENARIO_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "TRAINING007", "훈련 세션이 존재하는 시나리오는 삭제할 수 없습니다."),
    SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "TRAINING008", "이미 훈련 세션이 존재하는 시나리오입니다."),
    START_NODE_BUILDING_MISMATCH(HttpStatus.BAD_REQUEST, "TRAINING009", "시작 노드가 해당 시나리오의 건물에 속하지 않습니다."),
    START_NODE_NOT_CONFIGURED(HttpStatus.CONFLICT, "TRAINING010", "출발 노드가 지정되지 않은 시나리오는 훈련을 시작할 수 없습니다."),
    RUNNING_SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "TRAINING011", "해당 건물에서 이미 훈련이 진행 중입니다."),
    FIRE_ORIGIN_NOT_CONFIGURED(HttpStatus.CONFLICT, "TRAINING014", "발화 위치가 설정되지 않은 시나리오는 훈련을 시작할 수 없습니다."),
    START_NODE_TYPE_INVALID(HttpStatus.CONFLICT, "TRAINING015", "대피 시작 노드의 유형은 START여야 합니다."),
    FIRE_ORIGIN_START_FLOOR_MISMATCH(HttpStatus.CONFLICT, "TRAINING016", "발화 위치와 대피 시작 노드는 같은 층이어야 합니다."),
    FIRE_ORIGIN_ALREADY_CONFIGURED(HttpStatus.CONFLICT, "TRAINING017", "이미 최초 발화점이 설정된 시나리오입니다."),
    SCENARIO_EVACUATION_SETUP_ALREADY_EXISTS(HttpStatus.CONFLICT, "TRAINING018", "이미 발화점 및 시작점 설정이 완료된 시나리오입니다."),
    SCENARIO_DRAFT_NOT_ALLOWED(HttpStatus.CONFLICT, "TRAINING019", "작성이 완료되지 않은(DRAFT) 시나리오에서는 이 작업을 수행할 수 없습니다."),
    TRAINING_SCENARIO_REQUIRED_FIELD_MISSING(
            HttpStatus.BAD_REQUEST,
            "TRAINING_SCENARIO_REQUIRED_FIELD_MISSING",
            "시나리오 작성 완료에 필요한 값이 누락되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
