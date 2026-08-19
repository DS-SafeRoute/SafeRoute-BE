package com.saferoute.domain.congestion.entity;

public enum CongestionLevel {
    NORMAL,
    CAUTION,
    CROWDED,
    VERY_CROWDED;

    public boolean requiresRouteRecalculation() {
        return this == CROWDED || this == VERY_CROWDED;
    }
}
