package com.saferoute.domain.floor.entity;

import com.saferoute.domain.building.entity.Building;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "floors")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Floor {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Column(name = "floor_num", nullable = false)
    private Integer floorNum;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "map_image_url", length = 1000)
    private String mapImageUrl;

    //세그멘테이션 처리 상태 (PENDING, PROCESSING, DONE, FAILED)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "segmentation_status", nullable = false, length = 20)
    private SegmentationStatus segmentationStatus;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    private Floor(Building building, Integer floorNum, String mapImageUrl) {
        this.building = building;
        this.floorNum = floorNum;
        this.mapImageUrl = mapImageUrl;
        this.segmentationStatus = SegmentationStatus.PENDING;
    }

    // 도면(층) 등록용 정적 팩토리 메서드
    public static Floor create(Building building, Integer floorNum, String mapImageUrl) {
        return new Floor(building, floorNum, mapImageUrl);
    }
}