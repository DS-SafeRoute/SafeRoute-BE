package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateFireZoneRequest;
import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.service.FireZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

@Tag(name = "화재구역", description = "훈련 시나리오 발화점 등록·조회 API")
@RestController
@RequestMapping("/api/v1/scenarios/{scenarioId}/fire-zones")
@RequiredArgsConstructor
public class FireZoneController {

    private final FireZoneService fireZoneService;

    @Operation(
            summary = "발화점 지정",
            description = """
                    지정한 격자 셀(gridCellId)을 해당 시나리오의 최초 발화점으로 등록합니다.
                    등록과 동시에 그 셀은 FloorGridCell.isFired = true로 표시되며, 생성되는
                    FireZone은 isManualAdd = true, spreadGeneration = 0으로 저장됩니다.

                    이 API는 도면 관리 화면에서 READY 시나리오의 발화점을 격자 셀로
                    지정할 때 1회만 사용합니다. 시나리오당 최초 발화점은 하나만 허용되며,
                    이미 등록된 시나리오에 다시 요청하면 거부됩니다. 수정·삭제 API는 제공하지
                    않습니다. 시나리오 화면에서는 등록 API를 호출하지 않고 조회만 수행합니다.

                    이후 훈련이 시작되면 화재 확산 시뮬레이션이 이 발화점을
                    시작 세대(generation 0)로 삼아 BFS 방식으로 인접 셀에 옮겨붙으며,
                    isManualAdd = false인 FireZone들을 spreadGeneration을 늘려가며 추가로
                    생성합니다.

                    gridCellId로 지정한 셀이 속한 층의 건물이 시나리오의 건물과 다르면 요청이
                    거부됩니다. 발화 층에 START 노드가 하나 존재해야 하며, 등록할 때 해당
                    START 노드가 시나리오의 대피 시작 노드로 자동 연결됩니다.

                    훈련 세션이 종료(정상 종료/강제 종료/타임아웃)되면 이 시나리오의 화재 셀은
                    일괄적으로 isFired = false로 초기화됩니다.
                    """
    )
    @PostMapping
    public ResponseEntity<FireZoneResponse> designateOrigin(
            @PathVariable UUID scenarioId,
            @Valid @RequestBody CreateFireZoneRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fireZoneService.designateOrigin(scenarioId, request, authentication.getName()));
    }

    @Operation(
            summary = "화재구역 전체 조회",
            description = """
                    해당 시나리오에 등록된 FireZone 전체를 반환합니다. 관리자가 수동 지정한
                    최초 발화점(isManualAdd = true, spreadGeneration = 0)과, 훈련 시작 후 화재
                    확산 시뮬레이션이 BFS로 옮겨붙인 셀(isManualAdd = false)이 모두 포함됩니다.

                    spreadGeneration 오름차순, 같은 세대 안에서는 addedAt 오름차순으로
                    정렬되므로 그대로 화면에 그리면 시간순 확산 순서가 됩니다.

                    아직 발화점을 하나도 지정하지 않았다면 빈 배열을 반환합니다. 훈련 세션이
                    종료되면 화재 셀은 초기화되지만 FireZone 레코드 자체는 삭제되지 않으므로,
                    지난 훈련의 확산 기록 조회에도 사용할 수 있습니다.

                    시나리오 프론트는 최초 발화점만 필요한 경우
                    GET /api/v1/scenarios/{scenarioId}/fire-origin을 사용합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<List<FireZoneResponse>> getFireZones(
            @PathVariable UUID scenarioId,
            Authentication authentication) {
        return ResponseEntity.ok(fireZoneService.getFireZones(scenarioId, authentication.getName()));
    }
}
