package com.saferoute.domain.device.entity;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Table(
        name = "cctv_grid_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cctv_grid_cell",
                columnNames = {"cctv_id", "grid_cell_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CctvGridCell {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cctv_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cctv cctv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grid_cell_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FloorGridCell gridCell;

    private CctvGridCell(Cctv cctv, FloorGridCell gridCell) {
        if (cctv == null || gridCell == null) {
            throw new IllegalArgumentException("CCTV와 GridCell은 필수입니다.");
        }
        this.cctv = cctv;
        this.gridCell = gridCell;
    }

    public static CctvGridCell create(Cctv cctv, FloorGridCell gridCell) {
        return new CctvGridCell(cctv, gridCell);
    }
}
