package com.saferoute.domain.congestion.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.saferoute.domain.congestion.entity.CongestionConfig;

public record CongestionThresholdsResponse(
        @JsonProperty("CAUTION_FROM") Double cautionFrom,
        @JsonProperty("CROWDED_FROM") Double crowdedFrom,
        @JsonProperty("VERY_CROWDED_FROM") Double veryCrowdedFrom
) {

    public static CongestionThresholdsResponse from(CongestionConfig config) {
        return new CongestionThresholdsResponse(
                config.getCautionFrom(),
                config.getCrowdedFrom(),
                config.getVeryCrowdedFrom()
        );
    }
}
