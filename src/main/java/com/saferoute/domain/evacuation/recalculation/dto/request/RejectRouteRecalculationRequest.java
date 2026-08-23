package com.saferoute.domain.evacuation.recalculation.dto.request;

// 거절 사유는 선택 - 요청 Body 없이 보내도 거절은 처리된다.
public record RejectRouteRecalculationRequest(String reason) {
}
