package com.saferoute.global.api.error;

import com.saferoute.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum S3ErrorCode implements BaseErrorCode {

    EMPTY_FILE(HttpStatus.BAD_REQUEST, "S3_ERROR_001", "업로드할 파일이 비어 있습니다."),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3_ERROR_002", "S3 파일 업로드에 실패했습니다."),
    PRESIGNED_URL_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "S3_ERROR_003",
            "S3 업로드 URL 발급에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
