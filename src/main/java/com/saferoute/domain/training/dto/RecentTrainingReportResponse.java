package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.Grade;
import java.math.BigDecimal;
import java.time.Instant;

public record RecentTrainingReportResponse(
    String scenarioName,
    Instant startedAt,
    Integer participantCount,
    Integer avgEvacuationSec,
    BigDecimal survivalRate,
    Grade grade
) {

}
