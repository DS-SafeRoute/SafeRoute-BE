package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.TrainingScenario;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScenarioResponse {

    private UUID id;
    private String name;
    private UUID buildingId;
    private UUID adminId;
    private Integer expectedParticipants;
    private Instant scheduledAt;
    private Boolean isTemplate;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScenarioResponse from(TrainingScenario scenario) {
        return ScenarioResponse.builder()
                .id(scenario.getId())
                .name(scenario.getName())
                .buildingId(scenario.getBuildingId())
                .adminId(scenario.getAdminId())
                .expectedParticipants(scenario.getExpectedParticipants())
                .scheduledAt(scenario.getScheduledAt())
                .isTemplate(scenario.getIsTemplate())
                .createdAt(scenario.getCreatedAt())
                .updatedAt(scenario.getUpdatedAt())
                .build();
    }
}
