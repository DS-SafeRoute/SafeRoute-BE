package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserZoneErrorCode implements BaseErrorCode {

   USER_ZONE_NAME_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "USERZONE001", "이미 존재하는 사용자 영역입니다."),
   INVALID_GRID_CELL_REQUEST(HttpStatus.BAD_REQUEST, "USERZONE002", "잘못된 gridcell 요청입니다."),
   USER_ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "USERZONE003", "USERZONE을 찾을 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
