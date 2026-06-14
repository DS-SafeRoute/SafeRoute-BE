package com.saferoute.domain.building.exception;

import com.saferoute.global.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class BuildingNotFoundException extends BusinessException {
    public BuildingNotFoundException(UUID buildingId) {
        super(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다: " + buildingId);
    }
}