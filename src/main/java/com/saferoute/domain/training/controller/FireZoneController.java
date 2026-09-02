package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.service.FireZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "화재구역",
        description = "시나리오·훈련 화면에서 화재구역(최초 발화점 + 확산된 셀)을 조회하는 API. "
                + "최초 발화점과 훈련 시작점을 함께 설정하는 API는 시나리오 대피 설정 "
                + "(POST /api/v1/scenarios/{scenarioId}/evacuation-setup)을 사용한다."
)
@RestController
@RequestMapping("/api/v1/scenarios/{scenarioId}/fire-zones")
@RequiredArgsConstructor
public class FireZoneController {

    private final FireZoneService fireZoneService;

    @Operation(
            summary = "화재구역 전체 조회(훈련 표시용)",
            description = """
                    해당 시나리오에 등록된 FireZone 전체를 반환합니다. 관리자가 수동 지정한
                    최초 발화점(isManualAdd = true, spreadGeneration = 0)과, 훈련 시작 후 화재
                    확산 시뮬레이션이 BFS로 옮겨붙인 셀(isManualAdd = false)이 모두 포함됩니다.

                    spreadGeneration 오름차순, 같은 세대 안에서는 addedAt 오름차순으로
                    정렬되므로 그대로 화면에 그리면 시간순 확산 순서가 됩니다.

                    아직 발화점을 하나도 지정하지 않았다면 빈 배열을 반환합니다. 훈련 세션이
                    종료되면 화재 셀은 초기화되지만 FireZone 레코드 자체는 삭제되지 않으므로,
                    지난 훈련의 확산 기록 조회에도 사용할 수 있습니다.

                    훈련 진행 화면에서 최초 발화점과 확산된 화재 셀을 함께
                    표시할 때 사용합니다. 시나리오 설정 화면에서 발화점·시작점을 함께
                    조회하려면 GET /api/v1/scenarios/{scenarioId}/evacuation-setup을,
                    발화점만 단독으로 필요하면 GET /api/v1/scenarios/{scenarioId}/fire-origin을
                    사용합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<List<FireZoneResponse>> getFireZones(
            @PathVariable UUID scenarioId,
            Authentication authentication) {
        return ResponseEntity.ok(fireZoneService.getFireZones(scenarioId, authentication.getName()));
    }
}
