package com.saferoute.domain.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "fire_zones")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FireZone {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @NotNull
    @Column(name = "floor_map_id", nullable = false)
    private UUID floorMapId;

    @NotNull
    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @NotNull
    @Column(name = "is_manual_add", nullable = false)
    private Boolean isManualAdd = false;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public static FireZone create(UUID scenarioId, UUID floorMapId,
                                  UUID nodeId, Boolean isManualAdd) {
        FireZone zone = new FireZone();
        zone.scenarioId = scenarioId;
        zone.floorMapId = floorMapId;
        zone.nodeId = nodeId;
        zone.isManualAdd = isManualAdd != null ? isManualAdd : false;
        return zone;
    }
}
