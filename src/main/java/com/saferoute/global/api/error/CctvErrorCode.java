package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CctvErrorCode implements BaseErrorCode {

    CCTV_NOT_FOUND(HttpStatus.NOT_FOUND, "CCTV001", "CCTV를 찾을 수 없습니다."),
    GRID_CELL_NOT_FOUND(HttpStatus.NOT_FOUND, "CCTV002", "선택한 GridCell을 찾을 수 없습니다."),
    DUPLICATE_GRID_CELL(HttpStatus.BAD_REQUEST, "CCTV003", "중복된 GridCell이 포함되어 있습니다."),
    GRID_CELL_FLOOR_MISMATCH(HttpStatus.BAD_REQUEST, "CCTV004", "CCTV와 GridCell은 같은 층에 있어야 합니다."),
    NON_WALKABLE_GRID_CELL(HttpStatus.BAD_REQUEST, "CCTV005", "보행 가능하지 않은 GridCell은 감시 영역으로 설정할 수 없습니다."),
    GRID_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "CCTV006", "해당 층의 GridCell 크기가 설정되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
