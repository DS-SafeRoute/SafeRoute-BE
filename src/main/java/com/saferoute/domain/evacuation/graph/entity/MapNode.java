package com.saferoute.domain.evacuation.graph.entity;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "map_nodes")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapNode {

    @Id
    @GeneratedValue
    private UUID id;

    // 도면상 식별 코드 (예: "STAIR_A", "325") - AI 세그멘테이션 결과와 매칭용
    @NotBlank
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NodeType type;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "pos_x", nullable = false)
    private double x;

    @Column(name = "pos_y", nullable = false)
    private double y;

    // Dijkstra 경로 계산 시 목적지 후보로 삼을 노드인지 여부
    @Column(name = "is_exit_target", nullable = false)
    private boolean isExitTarget;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    private MapNode(Floor floor, String code, NodeType type, String name,
            double x, double y, boolean isExitTarget) {
        this.floor = floor;
        this.code = code;
        this.type = type;
        this.name = name;
        this.x = x;
        this.y = y;
        this.isExitTarget = isExitTarget;
    }

    // 그래프 노드 등록용 정적 팩토리 메서드
    public static MapNode create(Floor floor, String code, NodeType type, String name,
            double x, double y, boolean isExitTarget) {
        return new MapNode(floor, code, type, name, x, y, isExitTarget);
    }
}
