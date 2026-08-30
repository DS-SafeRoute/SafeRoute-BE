package com.saferoute.domain.training.dto;

import java.time.Instant;

import com.saferoute.domain.training.entity.FireSpreadSpeed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScenarioRequest {

    private String name;
    private Integer expectedParticipants;
    private Integer targetEvacuationSec;
    private Instant scheduledAt;
    private Boolean isTemplate;
    private FireSpreadSpeed fireSpreadSpeed;
}
