package com.saferoute.domain.evacuation.graph.dto.request;

import jakarta.validation.constraints.NotNull;

// 커스텀 편집 UI에서 드래그로 노드 위치 옮기거나 EXIT 대상 여부를 변경할 때 사용
public record UpdateMapNodePositionRequest(
        @NotNull Double x,
        @NotNull Double y,
        boolean isExitTarget
) {}
