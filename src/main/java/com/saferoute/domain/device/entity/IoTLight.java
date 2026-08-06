package com.saferoute.domain.device.entity;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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

// currentDirection(실시간 방향)은 훈련별 동적 상태라 여기 저장하지 않음 - EC2 서버 메모리에서 관리
//
// 위치 좌표와 소속 층의 단일 원천은 모두 customNode 다. (x, y, floor 중복 보관하지 않음)
//
// customNode   : 도면상 실제 기기 설치 위치 (등록 시 필수)
// decisionNode : 경로상 좌/우를 결정하는 분기 노드 (경로 설정 단계에서 지정, 최초엔 null)
// leftEdge / rightEdge : 좌/우 점등 시 안내되는 통로 (경로 설정 단계에서 지정, 최초엔 null)
@Entity
@Getter
@Table(name = "iot_lights")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IoTLight {

    @Id
    @GeneratedValue
    private UUID id;

    // 기기가 자동 보고하는 식별자. 시스템 전역에서 유일해야 한다.
    @NotBlank
    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 도면상 설치 위치 노드 (NodeType.CUSTOM)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_node_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapNode customNode;

    // 유도등이 방향을 결정하는 분기점 노드 (경로 설정 전에는 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_node_id")
    private MapNode decisionNode;

    // 왼쪽 유도등 ON 시 안내되는 통로 (경로 설정 전에는 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "left_edge_id")
    private MapEdge leftEdge;

    // 오른쪽 유도등 ON 시 안내되는 통로 (경로 설정 전에는 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "right_edge_id")
    private MapEdge rightEdge;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    // 이 유도등의 명령을 실제로 전달받는 라즈베리파이 주소 (예: http://192.168.0.50:5000)
    // 등록 시점에는 비어있을 수 있음 - 하드웨어 배치 후 별도로 설정
    @Column(name = "pi_endpoint", length = 255)
    private String piEndpoint;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private IoTLight(String code, String name, MapNode customNode) {
        validateCustomNode(customNode);
        this.code = code;
        this.name = name;
        this.customNode = customNode;
        this.enabled = true;
    }

    // 최초 기기 등록 - 도면 위치(customNode)만 있으면 등록 가능
    public static IoTLight create(String code, String name, MapNode customNode) {
        return new IoTLight(code, name, customNode);
    }

    // 관리자가 경로 설정 단계에서 분기 정보를 지정할 때 호출한다.
    // leftEdge/rightEdge가 실제로 decisionNode에 연결되어 있는지는 Service에서 검증한다.
    public void configureGuidance(MapNode decisionNode, MapEdge leftEdge, MapEdge rightEdge) {
        if (decisionNode == null) {
            throw new IllegalArgumentException("decisionNode는 필수입니다.");
        }
        if (leftEdge == null || rightEdge == null) {
            throw new IllegalArgumentException("leftEdge와 rightEdge는 함께 지정되어야 합니다.");
        }
        if (leftEdge.equals(rightEdge)) {
            throw new IllegalArgumentException("leftEdge와 rightEdge는 서로 달라야 합니다.");
        }
        this.decisionNode = decisionNode;
        this.leftEdge = leftEdge;
        this.rightEdge = rightEdge;
    }

    // 그래프가 재생성되어 기존 분기 설정이 무효해졌을 때
    public void clearGuidance() {
        this.decisionNode = null;
        this.leftEdge = null;
        this.rightEdge = null;
    }

    // 경로 안내에 실제로 사용할 수 있는 상태인지 (훈련 시작 전 점검용)
    public boolean isGuidanceConfigured() {
        return decisionNode != null && leftEdge != null && rightEdge != null;
    }

    public void relocate(MapNode customNode) {
        validateCustomNode(customNode);
        this.customNode = customNode;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    // 하드웨어 배치/교체 시 관리자가 라즈베리파이 주소를 지정한다.
    public void updatePiEndpoint(String piEndpoint) {
        if (piEndpoint == null || piEndpoint.isBlank()) {
            throw new IllegalArgumentException("piEndpoint는 비어있을 수 없습니다.");
        }
        this.piEndpoint = piEndpoint;
    }

    private static void validateCustomNode(MapNode customNode) {
        if (customNode == null) {
            throw new IllegalArgumentException("IoT 유도등의 customNode는 필수입니다.");
        }
        if (customNode.getType() != NodeType.CUSTOM) {
            throw new IllegalArgumentException("IoT 유도등의 customNode는 NodeType.CUSTOM 이어야 합니다.");
        }
    }
}