package com.saferoute.domain.evacuation.graph.entity;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
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
@Table(name = "map_edges")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapEdge {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "distance", nullable = false)
    private double distance;

    // 화재구역 통제 시 true로 전환 (Dijkstra 계산에서 이 엣지 제외)
    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    // IoT 유도등 디바이스 ID (IoT 디바이스 엔티티 생기면 FK 관계로 교체 예정) (미정)
    @Column(name = "guide_light_id")
    private UUID guideLightId;

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

    private MapEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance) {
        this.floor = floor;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.distance = distance;
        this.blocked = false;
    }

    // 그래프 엣지(통로) 등록용 정적 팩토리 메서드
    public static MapEdge create(Floor floor, MapNode fromNode, MapNode toNode, double distance) {
        return new MapEdge(floor, fromNode, toNode, distance);
    }
}
