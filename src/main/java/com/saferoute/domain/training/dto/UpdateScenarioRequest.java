package com.saferoute.domain.training.dto;

import java.time.Instant;
import java.util.UUID;

import com.saferoute.domain.training.entity.FireSpreadSpeed;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScenarioRequest {

    private String name;
    // DRAFT 작성 중 건물을 나중에 지정하거나 바꿀 때 사용한다. null이면 변경하지 않는다.
    private UUID buildingId;
    private Integer expectedParticipants;
    // null이면 변경하지 않음(TrainingScenario.update() 참고). 값을 지정할 땐 양수여야 한다.
    @Positive
    private Integer targetEvacuationSec;
    private Instant scheduledAt;
    private Boolean isTemplate;
    private FireSpreadSpeed fireSpreadSpeed;
}
