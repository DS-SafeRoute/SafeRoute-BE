package com.saferoute.domain.evacuation.recalculation.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.training.entity.TrainingSession;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 혼잡 감지로 트리거된 우회 경로 재탐색 결과. 관리자가 승인해야 실제 대피 경로에 반영된다.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", nullable = false, length = 20)
    private CongestionLevel congestionLevel;

    // 우회 경로를 이루는 노드 id 순서 (출발 -> 도착)
    @ElementCollection
    @CollectionTable(
            name = "route_recalculation_nodes",
            joinColumns = @JoinColumn(name = "route_recalculation_id")
    )
    @OrderColumn(name = "node_order")
    @Column(name = "node_id", nullable = false)
    private List<UUID> recalculatedNodeIds = new ArrayList<>();

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

    private RouteRecalculation(TrainingSession trainingSession, MapEdge triggerEdge,
            CongestionLevel congestionLevel, List<UUID> recalculatedNodeIds, double totalWeight) {
        this.trainingSession = trainingSession;
        this.triggerEdge = triggerEdge;
        this.congestionLevel = congestionLevel;
        this.recalculatedNodeIds = recalculatedNodeIds;
        this.totalWeight = totalWeight;
        this.status = RecalculationStatus.PENDING;
    }

    // 재탐색 결과를 승인 대기 상태로 저장하는 정적 팩토리 메서드
    public static RouteRecalculation createPending(TrainingSession trainingSession, MapEdge triggerEdge,
            CongestionLevel congestionLevel, List<UUID> recalculatedNodeIds, double totalWeight) {
        return new RouteRecalculation(trainingSession, triggerEdge, congestionLevel, recalculatedNodeIds,
                totalWeight);
    }

    // 상태 전이 검증은 이 엔티티가 아니라 RouteRecalculationService가 담당한다
    // (TrainingSessionService.start()/end()/forceEnd(), IoTLightService와 동일한 컨벤션).
    public void approve(Instant resolvedAt) {
        this.status = RecalculationStatus.APPROVED;
        this.resolvedAt = resolvedAt;
    }

    public void reject(Instant resolvedAt) {
        this.status = RecalculationStatus.REJECTED;
        this.resolvedAt = resolvedAt;
    }
}
