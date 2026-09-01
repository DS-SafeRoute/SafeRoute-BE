package com.saferoute.domain.training.dto;

import java.time.Instant;

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
    private Integer expectedParticipants;
    // null이면 변경하지 않음(TrainingScenario.update() 참고). 값을 지정할 땐 양수여야 한다.
    @Positive
    private Integer targetEvacuationSec;
    private Instant scheduledAt;
    private Boolean isTemplate;
    private FireSpreadSpeed fireSpreadSpeed;
}
