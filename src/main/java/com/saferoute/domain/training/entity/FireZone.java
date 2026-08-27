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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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

    // 시나리오 자체는 도면과 별개로 살아있어야 하므로 scenario는 CASCADE 걸지 않음
    // 도면이 지워져도 시나리오는 남고, 그 안의 이 FireZone만 정리되는걸로
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private TrainingScenario scenario;

    // 도면이 삭제되면 그 도면에 걸린 화재구역 설정도 삭제
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Floor floor;

    // 화재 확산 시뮬레이션이 셀 단위(BFS)로 도니까, 시작점부터 GridCell로 직결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grid_cell_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FloorGridCell gridCell;

    @NotNull
    @Column(name = "is_manual_add", nullable = false)
    private Boolean isManualAdd = false;

    @NotNull
    @Column(name = "spread_generation", nullable = false)
    private Integer spreadGeneration;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    private FireZone(TrainingScenario scenario, Floor floor, FloorGridCell gridCell,
                     Boolean isManualAdd, Integer spreadGeneration) {
        this.scenario = scenario;
        this.floor = floor;
        this.gridCell = gridCell;
        this.isManualAdd = isManualAdd != null ? isManualAdd : false;
        this.spreadGeneration = spreadGeneration;
    }

    // 화재구역 등록용 정적 팩토리 메서드
    public static FireZone create(TrainingScenario scenario, Floor floor, FloorGridCell gridCell, Boolean isManualAdd,  Integer spreadGeneration) {
        return new FireZone(scenario, floor, gridCell, isManualAdd, spreadGeneration);
    }

    // 관리자가 지정한 최초 발화점 등록용
    public static FireZone createOrigin(TrainingScenario scenario, Floor floor, FloorGridCell gridCell) {
        return new FireZone(scenario, floor, gridCell, true, 0);
    }

    // 확산 시뮬레이션이 새로 옮겨붙인 셀 등록용
    public static FireZone createSpread(TrainingScenario scenario, Floor floor, FloorGridCell gridCell, int generation) {
        return new FireZone(scenario, floor, gridCell, false, generation);
    }

    public UUID getScenarioId() { return scenario != null ? scenario.getId() : null; }
    public UUID getFloorId() { return floor != null ? floor.getId() : null; }
    public UUID getGridCellId() { return gridCell != null ? gridCell.getId() : null; }
}
