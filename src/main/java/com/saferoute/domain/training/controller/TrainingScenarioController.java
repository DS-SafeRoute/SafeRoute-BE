package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.dto.ScenarioResponse;
import com.saferoute.domain.training.dto.UpdateScenarioRequest;
import com.saferoute.domain.training.service.FireZoneService;
import com.saferoute.domain.training.service.TrainingScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "훈련 시나리오", description = "훈련 시나리오 등록/조회/수정/삭제 API")
@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class TrainingScenarioController {

    private final TrainingScenarioService scenarioService;
    private final FireZoneService fireZoneService;

    // GET /api/v1/scenarios
    @Operation(
            summary = "훈련 시나리오 목록 조회",
            description = """
                    요청자 학교 소속의 모든 훈련 시나리오를 생성일 최신순으로 반환합니다.

                    각 시나리오의 deletable은 해당 시나리오에 연결된 훈련 세션이 하나도 없을 때만
                    true입니다. 세션이 하나라도 있으면 과거 훈련 기록 보존을 위해 삭제가
                    막히므로, 프론트는 deletable = false인 시나리오에서는 삭제 버튼을 비활성화해야
                    합니다. reportId는 해당 시나리오의 훈련 리포트가 이미 생성되어 있으면 그
                    id를, 아직 없으면 null을 반환합니다.

                    status 필드는 연결된 세션의 생명주기에 따라 서버가 자동으로 갱신하는 값으로,
                    이 API로 직접 변경할 수 없습니다.
                    """
    )
    @GetMapping
    public ResponseEntity<List<ScenarioResponse>> getScenarios(Authentication authentication) {
        return ResponseEntity.ok(scenarioService.getScenarios(authentication.getName()));
    }

    // GET /api/v1/scenarios/{scenarioId}
    @Operation(
            summary = "훈련 시나리오 단건 조회",
            description = """
                    요청자 학교 소속 시나리오 중 scenarioId에 해당하는 시나리오 상세 정보를
                    반환합니다.

                    목록 조회와 달리 deletable, reportId 필드는 항상 null로 반환되므로 이 값은
                    목록 조회(GET /api/v1/scenarios) 결과를 사용해야 합니다.

                    다른 학교 소속이거나 존재하지 않는 scenarioId를 요청하면 조회에 실패합니다.
                    """
    )
    @GetMapping("/{scenarioId}")
    public ResponseEntity<ScenarioResponse> getScenario(
            @PathVariable UUID scenarioId,
            Authentication authentication) {
        return ResponseEntity.ok(scenarioService.getScenario(scenarioId, authentication.getName()));
    }

    // POST /api/v1/scenarios
    @Operation(
            summary = "훈련 시나리오 생성",
            description = """
                    새 훈련 시나리오를 생성합니다. buildingId, adminId는 모두 요청자와 같은
                    학교 소속이어야 하며, 두 값 중 하나라도 조건을 만족하지 못하면 생성에 실패합니다.

                    fireSpreadSpeed를 지정하지 않으면 MEDIUM으로 기본 처리됩니다. 이 값은
                    시나리오 전체에 하나로 적용되며(발화점별 개별 지정 불가), 훈련 시작 후 화재
                    확산 시뮬레이션의 tick 간격을 결정합니다(FAST가 가장 빠르게 확산).

                    startNodeId는 요청으로 받지 않습니다. 발화점을 등록하면 서버가 같은 층의
                    START 노드를 찾아 시나리오에 연결합니다. targetEvacuationSec도 선택값입니다.

                    생성 직후 시나리오의 상태는 READY이지만, 훈련을 시작하려면 발화점을
                    등록하고 같은 층의 START 노드가 시나리오에 연결되어야 합니다.
                    초안(DRAFT) 저장 플로우는 아직 지원하지 않습니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ScenarioResponse> createScenario(
            @Valid @RequestBody CreateScenarioRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scenarioService.createScenario(request, authentication.getName()));
    }

    // PATCH /api/v1/scenarios/{scenarioId}
    @Operation(
            summary = "훈련 시나리오 수정",
            description = """
                    요청 바디에 값이 채워진 필드만 부분 수정합니다. null인 필드는 기존 값을
                    그대로 유지하므로, 값을 지우고 싶다고 해서 null을 보내면 변경되지 않습니다.

                    startNodeId는 이 API로 변경하지 않습니다. 발화점 층의 START 노드를 서버가
                    자동으로 연결합니다. buildingId, adminId, status는 이 API로 변경할 수 없습니다.

                    시나리오의 status(READY/IN_PROGRESS/COMPLETED/ERROR)는 연결된 훈련
                    세션의 생명주기에 따라 서버가 자동으로 전이시키는 값이라 이 API로는 건드릴
                    수 없습니다. 이미 훈련 세션이 시작된 시나리오를 수정할 때의 부작용(예: 진행
                    중인 세션과의 정합성) 검증은 이 API에 포함되어 있지 않습니다.
                    """
    )
    @PatchMapping("/{scenarioId}")
    public ResponseEntity<ScenarioResponse> updateScenario(
            @PathVariable UUID scenarioId,
            @Valid @RequestBody UpdateScenarioRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(scenarioService.updateScenario(scenarioId, request, authentication.getName()));
    }

    // DELETE /api/v1/scenarios/{scenarioId}
    @Operation(
            summary = "훈련 시나리오 삭제",
            description = """
                    시나리오를 삭제합니다. 연결된 훈련 세션이 단 하나라도 존재하면(과거에 종료된
                    세션 포함) 과거 훈련 기록 보존을 위해 삭제가 거부되므로, 프론트는 목록
                    조회 응답의 deletable 필드로 삭제 가능 여부를 먼저 확인해야 합니다.

                    성공 시 본문 없이 204 No Content를 반환합니다.
                    """
    )
    @DeleteMapping("/{scenarioId}")
    public ResponseEntity<Void> deleteScenario(
            @PathVariable UUID scenarioId,
            Authentication authentication) {
        scenarioService.deleteScenario(scenarioId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/scenarios/{scenarioId}/fire-origin
    @Operation(
            summary = "최초 발화점 목록 조회",
            description = """
                    도면 관리에서 POST /api/v1/scenarios/{scenarioId}/fire-zones로 등록한
                    최초 발화점(FireZone.isManualAdd = true) 목록을 반환합니다.
                    시나리오당 최초 발화점은 최대 1개이므로 빈 배열 또는 원소 1개의 배열이
                    반환됩니다. 시나리오 화면은 이 API로 발화점을 조회해 표시만 합니다.

                    화재 확산 시뮬레이션이 생성한 FireZone(isManualAdd = false)은 포함되지
                    않으며, 아직 발화점을 하나도 지정하지 않았다면 빈 배열을 반환합니다.

                    다른 학교 소속이거나 존재하지 않는 scenarioId를 요청하면 조회에 실패합니다.
                    """
    )
    @GetMapping("/{scenarioId}/fire-origin")
    public ResponseEntity<List<FireZoneResponse>> getFireOrigin(
            @PathVariable UUID scenarioId,
            Authentication authentication) {
        return ResponseEntity.ok(fireZoneService.getFireOrigins(scenarioId, authentication.getName()));
    }
}
