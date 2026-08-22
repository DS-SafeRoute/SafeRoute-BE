package com.saferoute.domain.device.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonitoredAreaCalculatorTest {

    @Test
    @DisplayName("GridCell 개수와 셀 크기로 감시 면적을 계산한다")
    void calculate_returnsCellCountTimesSquaredCellSize() {
        Double area = MonitoredAreaCalculator.calculate(8, 0.5);

        assertThat(area).isEqualTo(2.0);
    }

    @Test
    @DisplayName("셀 크기가 설정되지 않았으면 null을 반환한다")
    void calculate_nullCellSize_returnsNull() {
        assertThat(MonitoredAreaCalculator.calculate(8, null)).isNull();
    }

    @Test
    @DisplayName("셀 크기가 0 이하이면 null을 반환한다")
    void calculate_nonPositiveCellSize_returnsNull() {
        assertThat(MonitoredAreaCalculator.calculate(8, 0.0)).isNull();
        assertThat(MonitoredAreaCalculator.calculate(8, -1.0)).isNull();
    }
}
