package com.saferoute.domain.user.exception;

import com.saferoute.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {
    public DuplicateEmailException(String email) {
        super(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다: " + email);
    }
}