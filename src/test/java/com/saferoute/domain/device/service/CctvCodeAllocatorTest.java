package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CctvCodeAllocatorTest {

    @Test
    void 세_자리까지는_0을_채우고_그_이후에는_자릿수를_확장한다() {
        assertThat(CctvCodeAllocator.format(1)).isEqualTo("CCTV_001");
        assertThat(CctvCodeAllocator.format(2)).isEqualTo("CCTV_002");
        assertThat(CctvCodeAllocator.format(999)).isEqualTo("CCTV_999");
        assertThat(CctvCodeAllocator.format(1_000)).isEqualTo("CCTV_1000");
    }

    @Test
    void 양수가_아닌_번호는_거부한다() {
        assertThatThrownBy(() -> CctvCodeAllocator.format(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
