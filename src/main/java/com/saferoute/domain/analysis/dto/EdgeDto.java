package com.saferoute.domain.analysis.dto;

public record EdgeDto(
    String fromTempId,
    String toTempId,
    double distance,
    boolean bidirectional
) {

}
