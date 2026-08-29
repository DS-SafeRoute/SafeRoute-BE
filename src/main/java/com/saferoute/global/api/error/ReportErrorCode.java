package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {

  REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT001", "훈련 리포트를 찾을 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
