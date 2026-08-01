package com.saferoute.domain.evacuation.graph.dto.request;

import jakarta.validation.constraints.NotNull;

// 커스텀 편집 UI에서 드래그로 노드 위치 옮길 때 사용
public record UpdateMapNodePositionRequest(
        @NotNull Double x,
        @NotNull Double y
) {}
