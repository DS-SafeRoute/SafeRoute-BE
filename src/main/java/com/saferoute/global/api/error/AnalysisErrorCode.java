package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisErrorCode implements BaseErrorCode {

  AI_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS001", "도면 분석에 실패했습니다."),
  AI_ANALYSIS_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "ANALYSIS002", "잘못된 요청입니다."),
  ANALYSIS_ALREADY_IN_PROGRESS(HttpStatus.BAD_REQUEST, "ANALYSIS003", "이미 분석중입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
