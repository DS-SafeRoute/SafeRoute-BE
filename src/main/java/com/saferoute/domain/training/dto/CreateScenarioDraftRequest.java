package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.FireSpreadSpeed;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// DRAFT 생성 요청. 시나리오 작성 화면 진입 시 미완성 상태로 임시 저장하는 용도라 모든 필드가
// 선택값이다. 작성자(admin)는 이 요청이 아니라 JWT 인증 사용자로 고정된다.
// targetEvacuationSec은 항상 10분(600초) 고정이라 요청 필드로 받지 않는다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateScenarioDraftRequest {

    @Size(min = 2, max = 20)
    private String name;

    private UUID buildingId;

    private Integer expectedParticipants;

    private Instant scheduledAt;

    private Boolean isTemplate = false;

    // 미지정 시 Service에서 MEDIUM으로 기본 처리
    private FireSpreadSpeed fireSpreadSpeed;
}
