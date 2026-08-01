package com.saferoute.domain.floor.entity;

import com.saferoute.domain.building.entity.Building;
import jakarta.persistence.*;
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
@Table(
    name = "floors",
    uniqueConstraints = @UniqueConstraint(name = "uk_floor_building_floornum", columnNames = {
        "building_id", "floor_num"})
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Floor {

  @Id
  @GeneratedValue
  private UUID id;

  @NotNull
  @Column(name = "floor_num", nullable = false)
  private Integer floorNum;

  // 도면 이미지 없이 층만 먼저 등록 가능한 설계이므로 @NotBlank를 걸지 않음 (null 허용)
  @Size(max = 1000)
  @Column(name = "map_image_key", length = 1000)
  private String mapImageKey;

  //세그멘테이션 처리 상태 (PENDING, PROCESSING, DONE, FAILED)
  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "segmentation_status", nullable = false, length = 20)
  private SegmentationStatus segmentationStatus;

  @Column(name = "processed_at")
  private Instant processedAt;

  // 화재 확산 Grid 계산용 — 세그멘테이션 완료 후 채워짐 (그 전엔 null)
  @Column(name = "grid_cell_size_meter")
  private Double gridCellSizeMeter;

  @Column(name = "grid_rows")
  private Integer gridRows;

  @Column(name = "grid_columns")
  private Integer gridColumns;

  // 원본 도면 픽셀 크기 (프론트 렌더링 좌표 변환용)
  @Column(name = "plan_width_px")
  private Integer planWidthPx;

  @Column(name = "plan_height_px")
  private Integer planHeightPx;

  @Column(name = "real_width")
  private Double realWidth;

  @Column(name = "real_height")
  private Double realHeight;
//픽셀값과 실제 길이는 도면 업로드시 같이 입력

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "building_id", nullable = false)
  private Building building;

  private Floor(Building building, Integer floorNum) {
    this.building = building;
    this.floorNum = floorNum;
    this.segmentationStatus = SegmentationStatus.PENDING;
  }

  // 도면(층) 등록용 정적 팩토리 메서드
  public static Floor create(Building building, Integer floorNum) {
    return new Floor(building, floorNum);
  }

  public void updateSegmentationStatus(SegmentationStatus status) {
    this.segmentationStatus = status;
  }

  public void upload(double realHeight,
      double realWidth, String mapImageKey) {
    this.realHeight = realHeight;
    this.realWidth = realWidth;
    this.mapImageKey = mapImageKey;
    this.segmentationStatus = SegmentationStatus.DONE;
  }

  // 세그멘테이션 완료 후 그리드 구성 정보 반영
  public void applyGridConfig(Double gridCellSizeMeter, Integer gridRows, Integer gridColumns,
      Integer planWidthPx, Integer planHeightPx) {
    this.gridCellSizeMeter = gridCellSizeMeter;
    this.gridRows = gridRows;
    this.gridColumns = gridColumns;
    this.planWidthPx = planWidthPx;
    this.planHeightPx = planHeightPx;
  }
}