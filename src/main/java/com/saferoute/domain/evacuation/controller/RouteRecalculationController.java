package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.recalculation.dto.request.RejectRouteRecalculationRequest;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationDetailResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationSummaryResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.EvacuationSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "재탐색 승인", description = "혼잡 재탐색 결과 조회/승인/거절 API")
@RestController
@RequestMapping("/api/v1/route-recalculations")
@RequiredArgsConstructor
public class RouteRecalculationController {

    private final RouteRecalculationService routeRecalculationService;

    @Operation(
            summary = "재탐색 요청 목록 조회",
            description = """
                    지정한 훈련 세션에서 혼잡 감지로 트리거된 경로 재탐색 요청 목록을
                    요청 시각(requestedAt) 내림차순(최신순)으로 반환합니다. 경로 상세(이전/후보
                    노드 목록)는 포함되지 않으며, 필요하면 상세 조회 API를 따로 호출해야 합니다.

                    status를 지정하면 해당 상태(PENDING/APPROVED/REJECTED/CANCELLED)의 요청만
                    필터링해 반환하고, 생략하면 전체 상태를 반환합니다. 같은 세션+엣지라도 혼잡
                    레벨이 오르내리며 PENDING이 CANCELLED로 무효화되고 새 PENDING이 다시 생성될
                    수 있어, 한 엣지에 대해 여러 건의 이력이 쌓일 수 있습니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteRecalculationSummaryResponse>>> getRecalculations(
            @RequestParam UUID trainingSessionId,
            @RequestParam(required = false) RecalculationStatus status,
            Authentication authentication
    ) {
        List<RouteRecalculationSummaryResponse> response =
                routeRecalculationService.getRecalculations(
                        trainingSessionId, status, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_LIST_FOUND, response));
    }

    @Operation(
            summary = "재탐색 요청 상세 조회",
            description = """
                    재탐색 요청 하나의 상세 정보를 반환합니다. 관리자가 승인/거절을 결정할 수
                    있도록 트리거 시점의 기존 경로(previousRoute)와 새로 계산된 후보 경로
                    (candidateRoute)를 노드 목록과 총 가중치로 나란히 제공합니다.

                    previousRoute는 트리거 시점에 이미 승인된 우회 경로가 있으면 그 경로를,
                    없으면 트리거 엣지를 그대로 포함한 정상(직행) 경로를 기준으로 삼습니다.
                    status가 PENDING이 아니면(APPROVED/REJECTED/CANCELLED) resolvedAt,
                    resolvedBy와 함께 rejectReason 또는 cancelReason이 채워지며, 시스템이
                    자동으로 CANCELLED 처리한 경우 resolvedBy는 null입니다.
                    """
    )
    @GetMapping("/{recalculationId}")
    public ResponseEntity<ApiResponse<RouteRecalculationDetailResponse>> getRecalculationDetail(
            @PathVariable UUID recalculationId,
            Authentication authentication
    ) {
        RouteRecalculationDetailResponse response =
                routeRecalculationService.getRecalculationDetail(
                        recalculationId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_DETAIL_FOUND, response));
    }

    @Operation(
            summary = "재탐색 후보 경로 승인",
            description = """
                    PENDING 상태인 재탐색 요청을 승인해 후보 경로(recalculatedNodeIds)를 실제
                    대피 경로로 확정합니다. 승인 즉시 그 경로를 따라 안내하도록 관련 IoT 유도등에
                    방향 반영이 트리거됩니다.

                    이미 APPROVED/REJECTED/CANCELLED로 처리된 요청을 다시 승인하려 하면
                    거부됩니다(PENDING만 승인 가능). 승인된 경로는 이후 같은 트리거 엣지에서
                    혼잡이 종료될 때 정상 경로로의 복구 후보를 계산하는 기준(활성 경로)으로도
                    사용됩니다.
                    """
    )
    @PatchMapping("/{recalculationId}/approve")
    public ResponseEntity<ApiResponse<RouteRecalculationResponse>> approve(
            @PathVariable UUID recalculationId,
            Authentication authentication
    ) {
        RouteRecalculationResponse response =
                routeRecalculationService.approve(recalculationId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_APPROVED, response));
    }

    @Operation(
            summary = "재탐색 후보 경로 거절",
            description = """
                    PENDING 상태인 재탐색 요청을 거절해 후보 경로를 반영하지 않고 기존 경로를
                    유지합니다. reason은 선택 항목이며, 요청 본문을 아예 생략해도(body 없이
                    호출해도) 거절 처리는 정상적으로 이루어집니다.

                    이미 APPROVED/REJECTED/CANCELLED 상태인 요청은 다시 거절할 수 없습니다
                    (PENDING만 거절 가능). 거절 사유는 상세 조회 API의 rejectReason으로 확인할
                    수 있습니다.
                    """
    )
    @PatchMapping("/{recalculationId}/reject")
    public ResponseEntity<ApiResponse<RouteRecalculationResponse>> reject(
            @PathVariable UUID recalculationId,
            Authentication authentication,
            @RequestBody(required = false) RejectRouteRecalculationRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        RouteRecalculationResponse response =
                routeRecalculationService.reject(recalculationId, authentication.getName(), reason);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.ROUTE_RECALCULATION_REJECTED, response));
    }
}
