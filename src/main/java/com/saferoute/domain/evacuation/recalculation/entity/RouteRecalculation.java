package com.saferoute.domain.evacuation.recalculation.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.user.entity.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 혼잡 감지로 트리거된 우회 경로 재탐색 결과. 관리자가 승인해야 실제 대피 경로에 반영된다.
//
// 같은 세션+엣지라도 혼잡 레벨이 오르내릴 때마다 PENDING -> CANCELLED -> 새 PENDING, 승인 후
// 다시 레벨이 오르면 그 엣지에 대해 두 번째 APPROVED가 생기는 식으로 이력이 여러 행 쌓일 수 있어
// (session, edge, status) 유니크 제약은 걸지 않는다 - "같은 세션+엣지에 PENDING 하나만" 규칙은
// RouteRecalculationService가 트리거 시점에 조회해서 지킨다.
@Entity
@Getter
@Table(name = "route_recalculations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteRecalculation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_session_id", nullable = false)
    private TrainingSession trainingSession;

    // 혼잡이 감지되어 이번 재탐색을 트리거한 엣지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_edge_id", nullable = false)
    private MapEdge triggerEdge;

    // 트리거를 발생시킨 CCTV
    @Column(name = "cctv_code", nullable = false, length = 50)
    private String cctvCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private RecalculationTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", nullable = false, length = 20)
    private CongestionLevel congestionLevel;

    // 트리거 시점에 BE가 계산한 density
    @Column(name = "density", nullable = false)
    private double density;

    // 트리거 시점의 활성 경로(가장 최근 APPROVED 경로, 없으면 트리거 엣지를 그대로 포함한 정상 경로)
    @ElementCollection
    @CollectionTable(
            name = "route_recalculation_previous_nodes",
            joinColumns = @JoinColumn(name = "route_recalculation_id")
    )
    @OrderColumn(name = "node_order")
    @Column(name = "node_id", nullable = false)
    private List<UUID> previousNodeIds;

    @Column(name = "previous_total_weight", nullable = false)
    private double previousTotalWeight;

    // 새로 계산된 후보 경로를 이루는 노드 id 순서 (출발 -> 도착)
    @ElementCollection
    @CollectionTable(
            name = "route_recalculation_nodes",
            joinColumns = @JoinColumn(name = "route_recalculation_id")
    )
    @OrderColumn(name = "node_order")
    @Column(name = "node_id", nullable = false)
    private List<UUID> recalculatedNodeIds;

    @Column(name = "total_weight", nullable = false)
    private double totalWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecalculationStatus status;

    @CreatedDate
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // 승인/거절한 관리자. 시스템이 자동으로 CANCELLED 처리한 경우엔 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    private RouteRecalculation(TrainingSession trainingSession, MapEdge triggerEdge, String cctvCode,
            RecalculationTriggerType triggerType, CongestionLevel congestionLevel, double density,
            List<UUID> previousNodeIds, double previousTotalWeight,
            List<UUID> recalculatedNodeIds, double totalWeight) {
        this.trainingSession = trainingSession;
        this.triggerEdge = triggerEdge;
        this.cctvCode = cctvCode;
        this.triggerType = triggerType;
        this.congestionLevel = congestionLevel;
        this.density = density;
        this.previousNodeIds = previousNodeIds;
        this.previousTotalWeight = previousTotalWeight;
        this.recalculatedNodeIds = recalculatedNodeIds;
        this.totalWeight = totalWeight;
        this.status = RecalculationStatus.PENDING;
    }

    // 재탐색 결과를 승인 대기 상태로 저장하는 정적 팩토리 메서드
    public static RouteRecalculation createPending(TrainingSession trainingSession, MapEdge triggerEdge,
            String cctvCode, RecalculationTriggerType triggerType, CongestionLevel congestionLevel, double density,
            List<UUID> previousNodeIds, double previousTotalWeight,
            List<UUID> recalculatedNodeIds, double totalWeight) {
        return new RouteRecalculation(trainingSession, triggerEdge, cctvCode, triggerType, congestionLevel, density,
                previousNodeIds, previousTotalWeight, recalculatedNodeIds, totalWeight);
    }

    // 상태 전이 검증은 이 엔티티가 아니라 RouteRecalculationService가 담당한다
    // (TrainingSessionService.start()/end()/forceEnd(), IoTLightService와 동일한 컨벤션).
    public void approve(Instant resolvedAt, User resolvedBy) {
        this.status = RecalculationStatus.APPROVED;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
    }

    public void reject(Instant resolvedAt, User resolvedBy, String reason) {
        this.status = RecalculationStatus.REJECTED;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.rejectReason = reason;
    }

    public void cancel(Instant resolvedAt, String reason) {
        this.status = RecalculationStatus.CANCELLED;
        this.resolvedAt = resolvedAt;
        this.cancelReason = reason;
    }
}
