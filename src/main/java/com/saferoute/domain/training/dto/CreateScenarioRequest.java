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

    // 훈련 리포트의 대피 시간 점수 산정 기준(초). 건물 규모에 따라 관리자가 지정
    @NotNull
    private Integer targetEvacuationSec;

    @NotNull
    private Instant scheduledAt;

    private Boolean isTemplate = false;

    @NotNull
    private UUID adminId;

    // 미지정 시 Service에서 MEDIUM으로 기본 처리
    private FireSpreadSpeed fireSpreadSpeed;

    // 훈련 시작 시 최초 대피 경로 계산의 출발 노드
    @NotNull
    private UUID startNodeId;
}
