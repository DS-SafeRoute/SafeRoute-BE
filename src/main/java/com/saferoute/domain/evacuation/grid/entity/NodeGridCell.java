package com.saferoute.domain.evacuation.grid.entity;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 특정 Grid Cell 영역에 어떤 MapNode가 있는지 빠르게 찾기 위한 정적 매핑 (표시/탐색용)
// MapEdgeGridCell(화재 확산 시뮬레이션용)과는 용도가 다름 - 얘는 "이 구역에 뭐가 있나" 조회 전용
@Entity
@Getter
@Table(
        name = "node_grid_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_node_grid_cell_node",
                columnNames = {"node_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeGridCell {

    @Id
    @GeneratedValue
    private UUID id;

    // 노드가 삭제되면 매핑도 함께 삭제
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapNode node;

    // 셀이 삭제되면 매핑도 함께 삭제
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grid_cell_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FloorGridCell gridCell;

    private NodeGridCell(MapNode node, FloorGridCell gridCell) {
        this.node = node;
        this.gridCell = gridCell;
    }

    public static NodeGridCell create(MapNode node, FloorGridCell gridCell) {
        return new NodeGridCell(node, gridCell);
    }

    // 노드가 다른 셀로 이동했을 때 (기기 위치 변경 등)
    public void changeGridCell(FloorGridCell gridCell) {
        this.gridCell = gridCell;
    }
}