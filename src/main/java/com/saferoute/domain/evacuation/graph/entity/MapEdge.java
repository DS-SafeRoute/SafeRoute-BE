package com.saferoute.domain.evacuation.graph.entity;

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
@Table(name = "map_edges")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapEdge {

    @Id
    @GeneratedValue
    private UUID id;

    // 실제 거리 (미터 단위, 0보다 커야 함)
    @Column(name = "distance", nullable = false)
    private double distance;

    // 양방향 통행 가능 여부
    @Column(name = "bidirectional", nullable = false)
    private boolean bidirectional;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 도면이 삭제되면 소속 엣지도 함께 삭제
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Floor floor;

    // fromNode/toNode가 지워질 때도 엣지가 같이 지워져야 한다 (Node CASCADE의 연쇄가
    // Edge까지 도달하지 못하므로 별도로 걸어준다. floor CASCADE와 경로가 겹쳐도 문제없음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapNode toNode;

    private MapEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance,
                    boolean bidirectional) {
        if (distance <= 0) {
            throw new IllegalArgumentException("distance는 0보다 커야 합니다.");
        }
        this.floor = floor;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.distance = distance;
        this.bidirectional = bidirectional;
    }

    // 그래프 엣지(통로) 등록용 정적 팩토리 메서드
    // blocked/fireRisk/congestion 등 훈련별 동적 값은 여기서 초기화하지 않음 (서버 메모리에서 관리)
    // 혼잡도는 MapEdgeGridCell로 연결된 FloorGridCell의 면적 기준으로 계산되므로 capacity를 두지 않는다.
    public static MapEdge create(Floor floor, MapNode fromNode, MapNode toNode,
                                 double distance, boolean bidirectional) {
        return new MapEdge(floor, fromNode, toNode, distance, bidirectional);
    }
}