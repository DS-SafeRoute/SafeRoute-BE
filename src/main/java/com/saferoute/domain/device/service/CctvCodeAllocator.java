package com.saferoute.domain.device.service;

import com.saferoute.domain.device.entity.CctvCodeAllocation;
import com.saferoute.domain.device.repository.CctvCodeAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CctvCodeAllocator {

    private static final String CCTV_CODE_PREFIX = "CCTV_";
    private static final int MINIMUM_NUMBER_WIDTH = 3;

    private final CctvCodeAllocationRepository allocationRepository;

    // 번호는 CCTV 등록 트랜잭션과 별도로 확정한다. 이후 등록이 실패하거나 CCTV가 삭제되어도
    // 이미 발급된 번호가 다시 사용되지 않으며, DB sequence가 동시 요청의 원자성을 보장한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String allocate() {
        CctvCodeAllocation allocation = allocationRepository.saveAndFlush(CctvCodeAllocation.create());
        return format(allocation.getNumber());
    }

    static String format(long number) {
        if (number < 1) {
            throw new IllegalArgumentException("CCTV code number must be positive");
        }
        return CCTV_CODE_PREFIX + String.format("%0" + MINIMUM_NUMBER_WIDTH + "d", number);
    }
}
