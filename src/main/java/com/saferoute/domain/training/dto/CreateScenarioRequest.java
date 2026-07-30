package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.FireSpreadSpeed;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateScenarioRequest {

    @NotBlank
    @Size(min = 2, max = 20)
    private String name;

    @NotNull
    private UUID buildingId;

    @NotNull
    private Integer expectedParticipants;

    @NotNull
    private Instant scheduledAt;

    private Boolean isTemplate = false;

    @NotNull
    private UUID adminId;

    // 미지정 시 Service에서 MEDIUM으로 기본 처리
    private FireSpreadSpeed fireSpreadSpeed;
}
