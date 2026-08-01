package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BuildingSuccessCode implements BaseCode {

    BUILDING_CREATED(HttpStatus.CREATED, "BUILDING_SUCCESS_001", "건물 등록에 성공했습니다."),
    BUILDING_LIST_FOUND(HttpStatus.OK, "BUILDING_SUCCESS_002", "건물 목록 조회에 성공했습니다."),
    BUILDING_DETAIL_FOUND(HttpStatus.OK, "BUILDING_SUCCESS_003", "건물 상세 조회에 성공했습니다."),
    BUILDING_UPDATED(HttpStatus.OK, "BUILDING_SUCCESS_004", "건물 정보 수정에 성공했습니다."),
    BUILDING_DEACTIVATED(HttpStatus.OK, "BUILDING_SUCCESS_005", "건물이 비활성화되었습니다."),
    BUILDING_DELETED(HttpStatus.OK, "BUILDING_SUCCESS_006", "건물이 삭제되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}