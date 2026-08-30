package com.saferoute.domain.training.controller;

import com.saferoute.domain.training.dto.CreateFireZoneRequest;
import com.saferoute.domain.training.dto.FireZoneResponse;
import com.saferoute.domain.training.service.FireZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "화재구역", description = "훈련 시나리오 발화점 지정 API")
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

                    이 API는 시나리오 설정(READY) 단계에서 관리자가 화면에서 발화점을 클릭해
                    지정할 때 사용합니다. 이후 훈련이 시작되면 화재 확산 시뮬레이션이 이 발화점을
                    시작 세대(generation 0)로 삼아 BFS 방식으로 인접 셀에 옮겨붙으며,
                    isManualAdd = false인 FireZone들을 spreadGeneration을 늘려가며 추가로
                    생성합니다.

                    gridCellId로 지정한 셀이 속한 층의 건물이 시나리오의 건물과 다르면 요청이
                    거부됩니다. 같은 시나리오에 여러 번 호출해 발화점을 추가로 지정할 수 있습니다.

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
}
