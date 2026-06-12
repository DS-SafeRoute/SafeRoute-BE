package com.saferoute.domain.building;

import com.saferoute.domain.training.entity.TrainingScenario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
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

    @NotNull
    @Column(name="total_floors", nullable = false)
    private Integer totalFloors;

    //건물 타입 (CLASSROOM, CAFETERIA 등)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 20)
    private BuildingType buildingType;

    @Column(name = "last_trained_at")
    private Instant lastTrainedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrainingScenario> trainingScenarios = new ArrayList<>();
}
