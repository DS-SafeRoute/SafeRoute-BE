package com.saferoute.domain.device.util;

// CCTV의 감시 면적(monitoredAreaM2) 계산. CctvResponse(관리자 응답)와
// 향후 Pi 설정 조회 API(이슈 6)가 동일한 계산을 공유하기 위해 분리했다.
public final class MonitoredAreaCalculator {

    private MonitoredAreaCalculator() {
    }

    // gridCellSizeMeter가 아직 설정되지 않은 층이면 면적을 계산할 수 없어 null을 반환한다.
    public static Double calculate(int gridCellCount, Double gridCellSizeMeter) {
        if (gridCellSizeMeter == null || gridCellSizeMeter <= 0) {
            return null;
        }
        return gridCellCount * gridCellSizeMeter * gridCellSizeMeter;
    }
}
