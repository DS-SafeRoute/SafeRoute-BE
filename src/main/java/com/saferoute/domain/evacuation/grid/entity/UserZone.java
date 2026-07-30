package com.saferoute.domain.evacuation.grid.entity;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

// 관리자가 여러 FloorGridCell을 묶어 사람이 읽을 수 있는 이름을 붙인 구역.
// 예: "301호 앞 복도", "3층 왼쪽 계단", "졸프실 앞 통로"
// (구역에 속한 셀 목록은 FloorGridCellRepository.findAllByUserZone_Id 로 조회)
@Entity
@Getter
@Table(
        name = "user_zones",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_zone_floor_name",
                columnNames = {"floor_id", "name"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserZone {

    @Id
    @GeneratedValue
    private UUID id;

    // 같은 층 안에서는 중복 불가, 다른 층에서는 같은 이름 사용 가능
    @NotBlank
    @Size(max = 50)
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 도면이 삭제되면 소속 구역도 함께 삭제
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Floor floor;

    private UserZone(Floor floor, String name) {
        this.floor = floor;
        this.name = name;
    }

    public static UserZone create(Floor floor, String name) {
        return new UserZone(floor, name);
    }

    public void rename(String name) {
        this.name = name;
    }
}