package com.saferoute.global.api.response;

import com.saferoute.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CctvSuccessCode implements BaseCode {

    CCTV_CREATED(HttpStatus.CREATED, "CCTV_SUCCESS_001", "CCTV가 등록되었습니다."),
    CCTV_LIST_FOUND(HttpStatus.OK, "CCTV_SUCCESS_002", "CCTV 목록 조회에 성공했습니다."),
    CCTV_DETAIL_FOUND(HttpStatus.OK, "CCTV_SUCCESS_003", "CCTV 상세 조회에 성공했습니다."),
    CCTV_GRID_CELLS_CONFIGURED(HttpStatus.OK, "CCTV_SUCCESS_004", "CCTV 감시 영역이 설정되었습니다."),
    CCTV_GRID_CELLS_FOUND(HttpStatus.OK, "CCTV_SUCCESS_005", "CCTV 감시 영역 조회에 성공했습니다."),
    CCTV_ENABLED(HttpStatus.OK, "CCTV_SUCCESS_006", "CCTV가 활성화되었습니다."),
    CCTV_DISABLED(HttpStatus.OK, "CCTV_SUCCESS_007", "CCTV가 비활성화되었습니다."),
    CCTV_UPDATED(HttpStatus.OK, "CCTV_SUCCESS_008", "CCTV 정보가 수정되었습니다."),
    CCTV_DELETED(HttpStatus.OK, "CCTV_SUCCESS_009", "CCTV가 삭제되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
