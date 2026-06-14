package com.saferoute.domain.floor.exception;

import com.saferoute.global.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FloorNotFoundException extends BusinessException {
    public FloorNotFoundException(UUID floorId) {
        super(HttpStatus.NOT_FOUND, "도면을 찾을 수 없습니다: " + floorId);
    }
}