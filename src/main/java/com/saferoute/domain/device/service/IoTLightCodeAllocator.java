package com.saferoute.domain.device.service;

import com.saferoute.domain.device.entity.IoTLightCodeAllocation;
import com.saferoute.domain.device.repository.IoTLightCodeAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IoTLightCodeAllocator {

    private static final String LIGHT_CODE_PREFIX = "LIGHT_";
    private static final int MINIMUM_NUMBER_WIDTH = 3;

    private final IoTLightCodeAllocationRepository allocationRepository;

    // 번호는 유도등 등록 트랜잭션과 별도로 확정한다 (CctvCodeAllocator와 동일한 컨벤션).
    // 등록이 실패하거나 유도등이 삭제되어도 이미 발급된 번호는 재사용되지 않으며,
    // DB sequence가 동시 요청의 원자성을 보장한다 - 기존 count()+1 방식은 동시 요청 시
    // 같은 번호를 중복 발급해 iot_lights_code_key 유니크 제약 위반을 일으켰다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String allocate() {
        IoTLightCodeAllocation allocation = allocationRepository.saveAndFlush(IoTLightCodeAllocation.create());
        return format(allocation.getNumber());
    }

    static String format(long number) {
        if (number < 1) {
            throw new IllegalArgumentException("IoT light code number must be positive");
        }
        return LIGHT_CODE_PREFIX + String.format("%0" + MINIMUM_NUMBER_WIDTH + "d", number);
    }
}
