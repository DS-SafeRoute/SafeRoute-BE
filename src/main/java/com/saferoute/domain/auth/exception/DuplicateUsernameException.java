package com.saferoute.domain.auth.exception;

import com.saferoute.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DuplicateUsernameException extends BusinessException {
    public DuplicateUsernameException(String username) {
        super(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다: " + username);
    }
}