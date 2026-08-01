package com.saferoute.domain.analysis.dto;

import java.util.List;
import java.util.Map;

public record AnalyseFloorResponse(
    int planWidthPx,
    int planHeightPx,
    List<NodeDto> nodes,
    List<EdgeDto> edges,
    Map<String, Object> stats
) {

}