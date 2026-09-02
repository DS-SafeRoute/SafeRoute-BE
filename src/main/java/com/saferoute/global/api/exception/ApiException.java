package com.saferoute.global.api.exception;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final BaseErrorCode errorCode;
    // 에러 응답 본문(ApiResponse.result)에 함께 실을 부가 정보. 대부분의 예외는 null이며,
    // 누락 필드 목록처럼 클라이언트가 후속 처리에 필요한 데이터가 있을 때만 채운다.
    private final Object result;

    public ApiException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.result = null;
    }

    public ApiException(BaseErrorCode errorCode, Object result) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.result = result;
    }

    public ApiException(BaseErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.result = null;
    }
}
