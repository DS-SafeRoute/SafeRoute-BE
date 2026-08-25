package com.saferoute.domain.building.entity;

import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.entity.TrainingScenario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "buildings")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building {
  @Id
  @GeneratedValue
  private UUID id;

    @NotBlank
    @Size(min = 2, max = 20)
    @Column(nullable = false, length = 20)
    private String name;

    @NotBlank
    @Size(min = 8, max = 100)
    @Column(nullable = false, length = 100)
    private String address;

    @NotBlank
    @Size(min = 5, max = 20)
    // 기존 건물 행에 소속 정보가 없어 ddl-auto:update 배포가 실패하지 않도록 DB 컬럼은
    // 우선 nullable로 추가한다. 신규 건물은 팩토리에서 항상 기관명을 받으며, null인 레거시 행은
    // 기관별 조회에서 노출되지 않으므로 운영 데이터는 배포 전후로 명시적으로 백필해야 한다.
    @Column(name = "school_name", length = 20)
    private String schoolName;

    // 지상층수/지하층수/총층수는 Floor 추가·삭제에 따라 자동 반영되며 클라이언트가 직접 수정할 수 없다.
    @NotNull
    @Column(name = "ground_floor_count", nullable = false)
    private Integer groundFloorCount;

    @NotNull
    @Column(name = "basement_floor_count", nullable = false)
    private Integer basementFloorCount;

    @NotNull
    @Column(name = "total_floors", nullable = false)
    private Integer totalFloors;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 20)
    private BuildingType buildingType;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "last_trained_at")
    private Instant lastTrainedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "building")
    private List<TrainingScenario> trainingScenarios = new ArrayList<>();

    // 도면(층)은 저장 전파만 허용, 삭제 전파는 하지 않는다.
    @OneToMany(mappedBy = "building", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Floor> floors = new ArrayList<>();

    private Building(String name, String address, BuildingType buildingType, String schoolName) {
        this.name = name;
        this.address = address;
        this.buildingType = buildingType;
        this.schoolName = schoolName;
        this.isActive = true;
        this.groundFloorCount = 0;
        this.basementFloorCount = 0;
        this.totalFloors = 0;
    }

    // 건물 등록용 정적 팩토리 메서드 (층수는 Floor 등록에 따라 채워짐)
    public static Building create(String name, String address, BuildingType buildingType, String schoolName) {
        return new Building(name, address, buildingType, schoolName);
    }

    // 건물 정보 수정 (층수는 대상에서 제외 — Floor 추가/삭제로만 변경됨)
    public void update(String name, String address, BuildingType buildingType) {
        this.name = name;
        this.address = address;
        this.buildingType = buildingType;
    }

    // Floor 등록 시 층수 반영
    public void addFloor(Integer floorNum) {
        if (isBasementFloor(floorNum)) {
            this.basementFloorCount++;
        } else {
            this.groundFloorCount++;
        }
        this.totalFloors = this.groundFloorCount + this.basementFloorCount;
    }

    // Floor 삭제 시 층수 반영
    public void removeFloor(Integer floorNum) {
        if (isBasementFloor(floorNum)) {
            this.basementFloorCount--;
        } else {
            this.groundFloorCount--;
        }
        this.totalFloors = this.groundFloorCount + this.basementFloorCount;
    }

    private boolean isBasementFloor(Integer floorNum) {
        return floorNum < 0;
    }

    // 건물 비활성화
    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
