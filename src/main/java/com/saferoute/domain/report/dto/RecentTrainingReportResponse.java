package com.saferoute.domain.report.dto;

import com.saferoute.domain.report.entity.Grade;
import java.math.BigDecimal;
import java.time.Instant;

public record RecentTrainingReportResponse(
    String reportId,
    String scenarioName,
    Instant startedAt,
    Integer participantCount,
    Integer avgEvacuationSec,
    BigDecimal survivalRate,
    Grade grade
) {

}
