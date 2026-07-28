package com.saferoute.domain.training.entity;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
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

    // 화재 확산 시뮬레이션이 셀 단위(BFS)로 도니까, 시작점부터 GridCell로 직결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grid_cell_id", nullable = false)
    private FloorGridCell gridCell;

    @NotNull
    @Column(name = "is_manual_add", nullable = false)
    private Boolean isManualAdd = false;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    private FireZone(TrainingScenario scenario, Floor floor, FloorGridCell gridCell, Boolean isManualAdd) {
        this.scenario = scenario;
        this.floor = floor;
        this.gridCell = gridCell;
        this.isManualAdd = isManualAdd != null ? isManualAdd : false;
    }

    // 화재구역 등록용 정적 팩토리 메서드
    public static FireZone create(TrainingScenario scenario, Floor floor, FloorGridCell gridCell, Boolean isManualAdd) {
        return new FireZone(scenario, floor, gridCell, isManualAdd);
    }

    public UUID getScenarioId() { return scenario != null ? scenario.getId() : null; }
    public UUID getFloorId() { return floor != null ? floor.getId() : null; }
    public UUID getGridCellId() { return gridCell != null ? gridCell.getId() : null; }
}
