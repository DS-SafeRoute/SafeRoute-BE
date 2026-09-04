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
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 설치 위치는 customNode가 관리하고, 실제 감시 영역은 CctvGridCell 매핑이 관리한다.
// 혼잡도 반영 경로: CCTV code -> CctvGridCell -> FloorGridCell -> MapEdgeGridCell -> MapEdge
// 소프트 삭제: deletedAt이 채워진 CCTV는 SQLRestriction으로 모든 조회(코드/토큰 인증 포함)에서
// 자동 제외된다. code는 사용 후 재사용하지 않는 순번 채번이라 삭제된 row가 남아 있어도 무방하다.
@Entity
@Getter
@Table(name = "cctvs")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at is null")
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

    @Column(name = "device_token_hash", length = 64, unique = true)
    private String deviceTokenHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    // 소프트 삭제. deletedAt 세팅 즉시 SQLRestriction으로 이후 모든 조회에서 제외된다.
    public void delete() {
        this.deletedAt = Instant.now();
    }

    public void issueDeviceToken(String deviceTokenHash) {
        if (deviceTokenHash == null || deviceTokenHash.isBlank()) {
            throw new IllegalArgumentException("디바이스 토큰 해시는 필수입니다.");
        }
        if (this.deviceTokenHash != null) {
            throw new IllegalStateException("디바이스 토큰은 이미 발급되었습니다.");
        }
        this.deviceTokenHash = deviceTokenHash;
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
