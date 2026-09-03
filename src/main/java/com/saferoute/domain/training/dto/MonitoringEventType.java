package com.saferoute.domain.training.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventType;

// 이벤트 타임라인(GET /api/v1/sessions/{sessionId}/monitoring/events)에 노출되는 이벤트 종류.
// 심각도와 사용자 표시 문구를 종류별로 이 열거형이 직접 정의한다 (이슈 #144).
public enum MonitoringEventType {
    CONGESTION_STARTED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.WARNING;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "혼잡 감지 · " + cctvCode;
        }
    },
    CONGESTION_LEVEL_UP {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return level == CongestionLevel.VERY_CROWDED
                    ? MonitoringEventSeverity.DANGER
                    : MonitoringEventSeverity.WARNING;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "혼잡 단계 상승 · " + cctvCode + " (" + level + ")";
        }
    },
    CONGESTION_ENDED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.INFO;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "혼잡 해소 · " + cctvCode;
        }
    },
    ROUTE_RECALCULATION_REQUESTED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.WARNING;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "경로 재탐색 요청 · " + cctvCode;
        }
    },
    EVACUATION_ROUTE_UPDATED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.INFO;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "대피 경로 갱신 승인 · " + cctvCode;
        }
    },
    ROUTE_RECALCULATION_REJECTED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.INFO;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "경로 재탐색 거절 · " + cctvCode;
        }
    },
    ROUTE_RECALCULATION_CANCELLED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.INFO;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "경로 재탐색 취소 · " + cctvCode;
        }
    },
    AI_ANALYSIS_STARTED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.INFO;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "AI 분석 시작 · " + cctvCode;
        }
    },
    ROUTE_DEVIATION_DETECTED {
        @Override
        public MonitoringEventSeverity severity(CongestionLevel level) {
            return MonitoringEventSeverity.WARNING;
        }

        @Override
        public String message(String cctvCode, CongestionLevel level) {
            return "경로 이탈 감지 · " + cctvCode;
        }
    };

    public abstract MonitoringEventSeverity severity(CongestionLevel level);

    public abstract String message(String cctvCode, CongestionLevel level);

    public static MonitoringEventType from(CongestionEventType congestionEventType) {
        return switch (congestionEventType) {
            case CONGESTION_STARTED -> CONGESTION_STARTED;
            case CONGESTION_LEVEL_UP -> CONGESTION_LEVEL_UP;
            case CONGESTION_ENDED -> CONGESTION_ENDED;
        };
    }

    public static MonitoringEventType from(GeneralMonitoringEventType generalMonitoringEventType) {
        return switch (generalMonitoringEventType) {
            case AI_ANALYSIS_STARTED -> AI_ANALYSIS_STARTED;
            case ROUTE_DEVIATION_DETECTED -> ROUTE_DEVIATION_DETECTED;
        };
    }

    // PENDING은 아직 해소되지 않은 상태라 타임라인의 "해소" 이벤트로 변환할 대상이 아니다.
    // 호출자(TrainingMonitoringService)가 resolvedAt이 있는 재탐색에 대해서만 호출해야 한다.
    public static MonitoringEventType fromResolution(RecalculationStatus status) {
        return switch (status) {
            case APPROVED -> EVACUATION_ROUTE_UPDATED;
            case REJECTED -> ROUTE_RECALCULATION_REJECTED;
            case CANCELLED -> ROUTE_RECALCULATION_CANCELLED;
            case PENDING -> throw new IllegalArgumentException("PENDING 상태는 해소 이벤트로 변환할 수 없습니다.");
        };
    }
}
