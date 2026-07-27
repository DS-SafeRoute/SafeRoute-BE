package com.saferoute.domain.device.entity;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
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

// CCTV 영상 ROI/스트리밍은 이 엔티티에 저장하지 않음 (엣지 디바이스가 로컬 처리, 서버로는 집계 수치만 전송)
@Entity
@Getter
@Table(
        name = "cctvs",
        uniqueConstraints = @UniqueConstraint(name = "uk_cctv_floor_code", columnNames = {"floor_id", "code"})
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cctv {

    @Id
    @GeneratedValue
    private UUID id;

    // 엣지 디바이스가 자동 보고하는 식별자 (MAC/시리얼 등). 관리자가 IP로 수동 등록하지 않음
    @NotBlank
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 도면 위 CCTV 아이콘 표시 좌표 (0.0~1.0)
    @Column(name = "pos_x", nullable = false)
    private double x;

    @Column(name = "pos_y", nullable = false)
    private double y;

    // 이 CCTV가 혼잡도를 감지하는 통로 (설치 위치와는 다른 의미)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitored_edge_id", nullable = false)
    private MapEdge monitoredEdge;

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

    private Cctv(Floor floor, String code, String name, double x, double y, MapEdge monitoredEdge) {
        this.floor = floor;
        this.code = code;
        this.name = name;
        this.x = x;
        this.y = y;
        this.monitoredEdge = monitoredEdge;
        this.enabled = true;
    }

    public static Cctv create(Floor floor, String code, String name, double x, double y, MapEdge monitoredEdge) {
        return new Cctv(floor, code, name, x, y, monitoredEdge);
    }

    // 관리자가 도면에서 감시 통로를 재지정할 때
    public void reassignMonitoredEdge(MapEdge monitoredEdge) {
        this.monitoredEdge = monitoredEdge;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}