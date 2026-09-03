package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateScenarioEvacuationSetupRequest;
import com.saferoute.domain.training.dto.ScenarioEvacuationSetupResponse;
import com.saferoute.domain.training.service.ScenarioEvacuationSetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "시나리오 대피 설정",
        description = "도면 관리 캔버스를 읽기 전용으로 재사용하는 시나리오 설정 화면에서, 최초 발화점과 훈련 시작점을 함께 설정/조회하는 API"
)
@RestController
@RequestMapping("/api/v1/scenarios/{scenarioId}/evacuation-setup")
@RequiredArgsConstructor
public class ScenarioEvacuationSetupController {

    private final ScenarioEvacuationSetupService scenarioEvacuationSetupService;

    @Operation(
            summary = "최초 발화점 + 훈련 시작점 설정",
            description = """
                    시나리오 설정 화면에서 사용자가 선택한 최초 발화점(fireOriginGridCellId)과
                    훈련 시작점(startNodeId)을 하나의 요청, 하나의 트랜잭션으로 함께 저장합니다.

                    fireOriginGridCellId는 격자 셀 ID이고, startNodeId는 도면 관리에서 미리
                    등록해 둔 NodeType.START 후보 노드 중 하나의 ID입니다. 이 API는 임의 좌표로
                    새 노드를 만들지 않으며, 두 값은 반드시 같은 층·같은 건물에 있어야 합니다.

                    시나리오 상태가 READY가 아니거나(작성이 아직 끝나지 않은 DRAFT 포함), 이미
                    발화점·시작점 설정이 완료된 시나리오에 다시 요청하면 실패합니다(409). 설정
                    후 수정·삭제 API는 제공하지 않으므로, 값을 바꾸려면 시나리오를 새로 만들어야
                    합니다.

                    DRAFT 시나리오는 POST /api/v1/scenarios/{scenarioId}/ready로 먼저 READY
                    전환해야 이 API를 호출할 수 있습니다.

                    이 API는 FloorGridCell.isFired를 true로 바꾸지 않습니다. 발화점은 시나리오별
                    정적 설정이고, isFired는 실제 훈련 중에만 의미 있는 동적 상태이기 때문입니다.
                    실제 화재 셀 활성화는 훈련 시작(POST /api/v1/sessions/{sessionId}/start)
                    시점에 이루어집니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ScenarioEvacuationSetupResponse> setup(
            @PathVariable UUID scenarioId,
            @Valid @RequestBody CreateScenarioEvacuationSetupRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scenarioEvacuationSetupService.setup(scenarioId, request, authentication.getName()));
    }

    @Operation(
            summary = "최초 발화점 + 훈련 시작점 조회",
            description = """
                    시나리오 설정 화면 재진입 시 발화점과 훈련 시작점을 한 번에 조회합니다.

                    아직 설정 전이면 fireOrigin, startNode, configuredAt이 모두 null인 응답을
                    200 OK로 반환합니다(404가 아닙니다). 프론트는 이 상태를 오류가 아니라
                    "아직 설정하지 않음"이라는 정상적인 화면 상태로 처리하면 됩니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ScenarioEvacuationSetupResponse> getSetup(
            @PathVariable UUID scenarioId,
            Authentication authentication) {
        return ResponseEntity.ok(scenarioEvacuationSetupService.get(scenarioId, authentication.getName()));
    }
}
