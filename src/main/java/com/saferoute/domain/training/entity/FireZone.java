package com.saferoute.domain.training.entity;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "fire_zones")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FireZone {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private TrainingScenario scenario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private MapNode node;

    @NotNull
    @Column(name = "is_manual_add", nullable = false)
    private Boolean isManualAdd = false;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    private FireZone(TrainingScenario scenario, Floor floor, MapNode node, Boolean isManualAdd) {
        this.scenario = scenario;
        this.floor = floor;
        this.node = node;
        this.isManualAdd = isManualAdd != null ? isManualAdd : false;
    }

    // 화재구역 등록용 정적 팩토리 메서드
    public static FireZone create(TrainingScenario scenario, Floor floor, MapNode node, Boolean isManualAdd) {
        return new FireZone(scenario, floor, node, isManualAdd);
    }

    public UUID getScenarioId() { return scenario != null ? scenario.getId() : null; }
    public UUID getFloorId() { return floor != null ? floor.getId() : null; }
    public UUID getNodeId() { return node != null ? node.getId() : null; }
}
