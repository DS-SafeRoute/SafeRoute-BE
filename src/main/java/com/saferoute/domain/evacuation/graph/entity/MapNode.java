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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(
        name = "map_nodes",
        uniqueConstraints = @UniqueConstraint(name = "uk_map_node_floor_code", columnNames = {"floor_id", "code"})
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapNode {

    @Id
    @GeneratedValue
    private UUID id;

    // 도면상 식별 코드 (예: "STAIR_A", "NODE_001") - 서버가 자동 생성한다.
    // 순번 기반 코드는 DB 조회가 필요하므로 엔티티가 아닌 Service에서 생성해 넘긴다.
    @NotBlank
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_device_type", length = 20)
    private CustomDeviceType customDeviceType; // type == CUSTOM일 때만 값 존재

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NodeType type;

    // UI에 표시할 사람이 읽는 위치명 (예: "왼쪽 복도", "301호 앞") - 층 번호는 포함하지 않음
    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 0.0~1.0 정규화 좌표 (도면 가로/세로 기준)
    // CCTV / IoT 유도등의 도면상 좌표도 이 값을 사용한다.
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

    // 도면이 삭제되면 소속 노드도 함께 삭제 (DB ON DELETE CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
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

    // CCTV / IoT 유도등 설치 위치용 노드 생성.
    // 기기 위치 노드는 경로 목적지가 될 수 없으므로 isExitTarget은 항상 false다.
    public static MapNode createCustom(Floor floor, String code, String name, double x, double y) {
        return new MapNode(floor, code, NodeType.CUSTOM, name, x, y, false);
    }

    // CCTV/유도등 구분이 필요한 새 호출부용 오버로드
    public static MapNode createCustom(Floor floor, String code, String name, double x, double y,
                                       CustomDeviceType customDeviceType) {
        MapNode node = new MapNode(floor, code, NodeType.CUSTOM, name, x, y, false);
        node.customDeviceType = customDeviceType;
        return node;
    }

    // 도면에서 노드를 이동시킬 때 (기기 위치 변경 포함)
    public void moveTo(double x, double y) {
        this.x = x;
        this.y = y;
    }
}