package com.saferoute.domain.device.service;

import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.DeviceErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceAuthorizationService {

    private final CctvJpaRepository cctvJpaRepository;

    public void validateCctv(DevicePrincipal principal, String requestedCctvCode) {
        Cctv requestedCctv = cctvJpaRepository.findByCode(requestedCctvCode)
                .orElseThrow(() -> new ApiException(CctvErrorCode.CCTV_NOT_FOUND));

        if (!requestedCctv.getId().equals(principal.cctvId())) {
            throw new ApiException(DeviceErrorCode.CCTV_CODE_MISMATCH);
        }
        if (!requestedCctv.isEnabled()) {
            throw new ApiException(DeviceErrorCode.CCTV_DISABLED);
        }
    }
}
