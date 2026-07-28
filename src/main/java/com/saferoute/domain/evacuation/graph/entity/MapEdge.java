package com.saferoute.domain.evacuation.graph.entity;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
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
        name = "map_edges",
        uniqueConstraints = @UniqueConstraint(name = "uk_map_edge_from_to", columnNames = {"from_node_id", "to_node_id"})
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapEdge {

    @Id
    @GeneratedValue
    private UUID id;

    // 실제 거리 (미터 단위, 0보다 커야 함)
    @Column(name = "distance", nullable = false)
    private double distance;

    // 통로 기준 수용 인원 (1 이상)
    @Column(name = "capacity", nullable = false)
    private int capacity;

    // 양방향 통행 가능 여부
    @Column(name = "bidirectional", nullable = false)
    private boolean bidirectional;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private MapNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private MapNode toNode;

    private MapEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance,
                    int capacity, boolean bidirectional) {
        if (distance <= 0) {
            throw new IllegalArgumentException("distance는 0보다 커야 합니다.");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity는 1 이상이어야 합니다.");
        }
        // self-loop 방지: 같은 노드로 시작/도착하는 엣지는 A* 계산 시 무한 루프를 유발함
        if (Objects.equals(fromNode.getId(), toNode.getId())) {
            throw new IllegalArgumentException("fromNode와 toNode는 서로 달라야 합니다. (self-loop 불가)");
        }
        // 서로 다른 층에 속한 노드끼리 엣지를 만들면 존재하지 않는 층간 이동 경로가 생성됨
        if (!fromNode.getFloor().getId().equals(floor.getId())
                || !toNode.getFloor().getId().equals(floor.getId())) {
            throw new IllegalArgumentException("fromNode와 toNode는 엣지가 속한 층(floor)과 동일해야 합니다.");
        }
        this.floor = floor;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.distance = distance;
        this.capacity = capacity;
        this.bidirectional = bidirectional;
    }

    // 그래프 엣지(통로) 등록용 정적 팩토리 메서드
    // blocked/fireRisk/congestion 등 훈련별 동적 값은 여기서 초기화하지 않음 (서버 메모리에서 관리)
    public static MapEdge create(Floor floor, MapNode fromNode, MapNode toNode,
                                 double distance, int capacity, boolean bidirectional) {
        return new MapEdge(floor, fromNode, toNode, distance, capacity, bidirectional);
    }
}
