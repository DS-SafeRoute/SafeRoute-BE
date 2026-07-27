package com.saferoute.domain.device.entity;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// currentDirection(실시간 방향)은 훈련별 동적 상태라 여기 저장하지 않음 - EC2 서버 메모리에서 관리
@Entity
@Getter
@Table(
        name = "iot_lights",
        uniqueConstraints = @UniqueConstraint(name = "uk_iot_light_floor_code", columnNames = {"floor_id", "code"})
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IoTLight {

    @Id
    @GeneratedValue
    private UUID id;

    @NotBlank
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "pos_x", nullable = false)
    private double x;

    @Column(name = "pos_y", nullable = false)
    private double y;

    // 유도등이 설치된 분기점 노드
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_node_id", nullable = false)
    private MapNode decisionNode;

    // 왼쪽 유도등 ON 시 안내되는 통로
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "left_edge_id", nullable = false)
    private MapEdge leftEdge;

    // 오른쪽 유도등 ON 시 안내되는 통로
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "right_edge_id", nullable = false)
    private MapEdge rightEdge;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    private IoTLight(Floor floor, String code, String name, double x, double y,
                     MapNode decisionNode, MapEdge leftEdge, MapEdge rightEdge) {
        this.floor = floor;
        this.code = code;
        this.name = name;
        this.x = x;
        this.y = y;
        this.decisionNode = decisionNode;
        this.leftEdge = leftEdge;
        this.rightEdge = rightEdge;
        this.enabled = true;
    }

    public static IoTLight create(Floor floor, String code, String name, double x, double y,
                                  MapNode decisionNode, MapEdge leftEdge, MapEdge rightEdge) {
        return new IoTLight(floor, code, name, x, y, decisionNode, leftEdge, rightEdge);
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}