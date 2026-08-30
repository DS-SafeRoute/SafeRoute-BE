package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FloorSuccessCode implements BaseCode {

    FLOOR_CREATED(HttpStatus.CREATED, "FLOOR_SUCCESS_001", "도면 등록에 성공했습니다."),
    FLOOR_LIST_FOUND(HttpStatus.OK, "FLOOR_SUCCESS_002", "도면 목록 조회에 성공했습니다."),
    FLOOR_DETAIL_FOUND(HttpStatus.OK, "FLOOR_SUCCESS_003", "도면 상세 조회에 성공했습니다."),
    FLOOR_DELETED(HttpStatus.OK, "FLOOR_SUCCESS_004", "도면이 삭제되었습니다."),
    FLOOR_UPDATED(HttpStatus.OK, "FLOOR_SUCCESS_005", "도면 정보 수정에 성공했습니다."),
    FLOOR_IMAGE_URL_ISSUED(HttpStatus.OK, "FLOOR_SUCCESS_006", "도면 이미지 URL 발급에 성공했습니다."),
    FLOOR_MAP_CLEARED(HttpStatus.OK, "FLOOR_SUCCESS_007", "도면이 초기화되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}