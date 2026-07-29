package com.saferoute.domain.evacuation.grid.entity;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 특정 화재 Grid Cell이 어떤 MapEdge를 차단하는지 빠르게 찾기 위한 정적 매핑
@Entity
@Getter
@Table(
        name = "map_edge_grid_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_edge_grid_cell",
                columnNames = {"map_edge_id", "grid_cell_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapEdgeGridCell {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_edge_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapEdge mapEdge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grid_cell_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FloorGridCell gridCell;

    private MapEdgeGridCell(MapEdge mapEdge, FloorGridCell gridCell) {
        this.mapEdge = mapEdge;
        this.gridCell = gridCell;
    }

    public static MapEdgeGridCell create(MapEdge mapEdge, FloorGridCell gridCell) {
        return new MapEdgeGridCell(mapEdge, gridCell);
    }
}