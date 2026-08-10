package com.saferoute.domain.analysis.dto;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalyseFloorResponse(
    int planWidthPx,
    int planHeightPx,
    List<NodeDto> nodes,
    List<EdgeDto> edges,
    Map<String, Object> stats
) {

}