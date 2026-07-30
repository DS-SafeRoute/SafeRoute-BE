package com.saferoute.domain.device.entity;

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

// 혼잡도 반영 경로: CCTV code -> customNode -> NodeGridCell -> FloorGridCell -> MapEdgeGridCell -> MapEdge
@Entity
@Getter
@Table(name = "cctvs")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cctv {

    @Id
    @GeneratedValue
    private UUID id;

    // 엣지 디바이스가 자동 보고하는 식별자 (MAC/시리얼 등). 관리자가 IP로 수동 등록하지 않음.
    // 라즈베리파이가 이 값만 보내오므로 시스템 전역에서 유일해야 한다.
    @NotBlank
    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 도면상 설치 위치 노드(CUSTOM). 좌표와 층 정보의 단일 원천.
    // customNode가 CASCADE로 지워질 때 Cctv/IoTLight도 같이 지워지게함.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_node_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapNode customNode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Cctv(String code, String name, MapNode customNode) {
        validateCustomNode(customNode);
        this.code = code;
        this.name = name;
        this.customNode = customNode;
        this.enabled = true;
    }

    // 기기 등록용 정적 팩토리 메서드. 층은 customNode 로부터 결정되므로 따로 받지 않는다.
    public static Cctv create(String code, String name, MapNode customNode) {
        return new Cctv(code, name, customNode);
    }

    // 도면에서 기기 위치를 옮겼을 때 (새 CUSTOM 노드로 교체)
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

    // FK만으로는 NodeType을 강제할 수 없으므로 생성/변경 시점에 도메인 레벨에서 검증한다.
    // Service에서 getReferenceById로 얻은 프록시를 넘기지 말 것 (getType() 접근 시 초기화 발생)
    private static void validateCustomNode(MapNode customNode) {
        if (customNode == null) {
            throw new IllegalArgumentException("CCTV의 customNode는 필수입니다.");
        }
        if (customNode.getType() != NodeType.CUSTOM) {
            throw new IllegalArgumentException("CCTV의 customNode는 NodeType.CUSTOM 이어야 합니다.");
        }
    }
}