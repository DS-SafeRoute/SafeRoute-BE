package com.saferoute.domain.evacuation.grid.entity;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "cell_type", nullable = false, length = 20)
    private GridCellType cellType;

    @Column(name = "walkable", nullable = false)
    private boolean walkable;

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
    private Floor floor;

    private FloorGridCell(Floor floor, int rowIndex, int columnIndex, GridCellType cellType,
                          boolean walkable, double centerX, double centerY) {
        this.floor = floor;
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.cellType = cellType;
        this.walkable = walkable;
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public static FloorGridCell create(Floor floor, int rowIndex, int columnIndex,
                                       GridCellType cellType, boolean walkable, double centerX, double centerY) {
        return new FloorGridCell(floor, rowIndex, columnIndex, cellType, walkable, centerX, centerY);
    }
}