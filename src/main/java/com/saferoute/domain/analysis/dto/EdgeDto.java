package com.saferoute.domain.analysis.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EdgeDto(
    String fromTempId,
    String toTempId,
    double distance,
    boolean bidirectional
) {

}
