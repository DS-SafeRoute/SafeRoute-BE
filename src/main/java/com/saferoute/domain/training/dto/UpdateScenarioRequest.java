package com.saferoute.domain.training.dto;

import java.time.Instant;
import java.util.UUID;

import com.saferoute.domain.training.entity.FireSpreadSpeed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// targetEvacuationSec은 항상 10분(600초) 고정이라 요청 필드로 받지 않는다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScenarioRequest {

    private String name;
    // DRAFT 작성 중 건물을 나중에 지정하거나 바꿀 때 사용한다. null이면 변경하지 않는다.
    private UUID buildingId;
    private Integer expectedParticipants;
    private Instant scheduledAt;
    private Boolean isTemplate;
    private FireSpreadSpeed fireSpreadSpeed;
}
