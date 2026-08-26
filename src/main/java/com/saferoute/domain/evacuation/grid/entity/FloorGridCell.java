package com.saferoute.domain.evacuation.grid.entity;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(
        name = "floor_grid_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grid_cell_floor_row_col",
                columnNames = {"floor_id", "row_index", "column_index"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FloorGridCell {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    @Column(name = "walkable", nullable = false)
    private boolean walkable;

    // 현재 화재 여부 (훈련 중 동적으로 변경, 훈련 종료 시 초기화 필요)
    @Column(name = "isFired", nullable = false)
    private boolean isFired;

    // 0.0~1.0 정규화 좌표 (셀 중심점)
    @Column(name = "center_x", nullable = false)
    private double centerX;

    @Column(name = "center_y", nullable = false)
    private double centerY;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Floor floor;

    // 관리자가 여러 셀을 묶어 이름 붙인 구역 (예: "301호 앞 복도"). 지정 전에는 null.
    // UserZone이 삭제되면 이 셀은 구역 미지정 상태로 남아야 하므로 SET NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_zone_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserZone userZone;

    private FloorGridCell(Floor floor, int rowIndex, int columnIndex,
                          boolean walkable, double centerX, double centerY) {
        this.floor = floor;
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.walkable = walkable;
        this.centerX = centerX;
        this.centerY = centerY;
        this.isFired = false;
    }

    public static FloorGridCell create(Floor floor, int rowIndex, int columnIndex,
                                       boolean walkable, double centerX, double centerY) {
        return new FloorGridCell(floor, rowIndex, columnIndex, walkable, centerX, centerY);
    }

    // 화재 확산 시뮬레이션에서 이 셀에 불이 붙었을 때
    public void markFired() {
        this.isFired = true;
    }

    // 훈련 종료 후 화재 상태 초기화
    public void resetFire() {
        this.isFired = false;
    }

    // 사용자 지정 구역에 편입 (같은 Floor인지는 Service에서 검증한다)
    public void assignUserZone(UserZone userZone) {
        this.userZone = userZone;
    }

    public void releaseUserZone() {
        this.userZone = null;
    }
}