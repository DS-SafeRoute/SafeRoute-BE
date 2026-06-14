package com.saferoute.domain.training.entity;

import com.saferoute.domain.building.Building;
import com.saferoute.domain.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "training_scenarios")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingScenario {

    @Id
    @GeneratedValue
    private UUID id;

    @NotBlank
    @Size(min = 2, max = 20)
    @Column(nullable = false, length = 20)
    private String name;

    @NotNull
    @Column(name = "exp_participants", nullable = false)
    private Integer expectedParticipants;

    @NotNull
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "is_template", nullable = false)
    private Boolean isTemplate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    // ERD 기준 admin_id (팀원 원본 코드의 user_id에서 변경)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TrainingScenario create(String name,
                                          Integer expectedParticipants,
                                          Instant scheduledAt,
                                          Boolean isTemplate,
                                          Building building,
                                          User admin) {
        TrainingScenario scenario = new TrainingScenario();
        scenario.name = name;
        scenario.expectedParticipants = expectedParticipants;
        scenario.scheduledAt = scheduledAt;
        scenario.isTemplate = isTemplate != null ? isTemplate : false;
        scenario.building = building;
        scenario.admin = admin;
        return scenario;
    }

    public void update(String name,
                       Integer expectedParticipants,
                       Instant scheduledAt,
                       Boolean isTemplate) {
        if (name != null) this.name = name;
        if (expectedParticipants != null) this.expectedParticipants = expectedParticipants;
        if (scheduledAt != null) this.scheduledAt = scheduledAt;
        if (isTemplate != null) this.isTemplate = isTemplate;
    }

    // 응답용 getter
    public UUID getBuildingId() {
        return building != null ? building.getId() : null;
    }

    @OneToMany(mappedBy = "trainingScenario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrainingSession> trainingSessions = new ArrayList<>();
    public UUID getAdminId() {
        return admin != null ? admin.getId() : null;
    }
}