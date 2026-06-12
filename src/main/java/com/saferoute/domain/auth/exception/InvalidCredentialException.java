package com.saferoute.domain.auth.exception;

import com.saferoute.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialException extends BusinessException {
    public InvalidCredentialException() {
        super(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}