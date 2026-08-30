package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.ConfigureCctvGridCellsRequest;
import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.request.UpdateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.dto.response.CctvRegistrationResponse;
import com.saferoute.domain.device.dto.response.DeviceTokenIssueResponse;
import com.saferoute.domain.device.service.CctvService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.CctvSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CCTV", description = "CCTV 등록/조회 및 감시 GridCell 설정 API")
@RestController
@RequestMapping("/api/v1/cctvs")
@RequiredArgsConstructor
public class CctvController {

    private final CctvService cctvService;

    @Operation(
            summary = "CCTV 등록",
            description = """
                    CCTV를 새로 등록하고 감시 GridCell을 함께 지정합니다. code는 클라이언트가
                    보내지 않으며 서버가 CCTV_001 형식으로 자동 채번하고, DB sequence 기반이라
                    등록이 실패하거나 이후 CCTV가 삭제되어도 같은 번호가 재사용되지 않습니다.

                    등록과 동시에 디바이스 토큰을 발급해 응답의 deviceToken 필드로 평문 그대로
                    한 번만 반환합니다. 이 값은 서버에 해시로만 저장되므로 이후에는 다시 조회할
                    수 없고, 분실 시 별도 재발급 절차가 필요합니다.

                    floorId가 가리키는 층에 그리드(gridCellSizeMeter)가 아직 설정되지 않았으면
                    등록할 수 없습니다. gridCellIds는 비어 있을 수 없고, 중복되거나 해당 층 소속이
                    아니거나 보행 불가능(walkable=false) 셀이 섞여 있으면 실패합니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CctvRegistrationResponse>> createCctv(
            @Valid @RequestBody CreateCctvRequest request
    ) {
        CctvRegistrationResponse response = cctvService.createCctv(request);
        return ResponseEntity.status(CctvSuccessCode.CCTV_CREATED.getHttpStatus())
                .body(ApiResponse.success(CctvSuccessCode.CCTV_CREATED, response));
    }

    @Operation(
            summary = "CCTV 디바이스 토큰 발급",
            description = """
                    지정한 CCTV의 디바이스 토큰을 발급해 평문 값을 한 번만 반환합니다.
                    라즈베리파이가 이 토큰으로 자기 자신을 인증하므로, 등록 API가 발급한 토큰을
                    분실했거나 등록 당시 토큰 없이 생성된 CCTV에 사용합니다.

                    이미 토큰이 발급된 CCTV(deviceTokenHash가 이미 존재)에는 호출할 수 없고
                    실패합니다 - 재발급이 아니라 아직 토큰이 없는 CCTV에 대한 최초 발급 전용
                    API입니다. 발급된 값은 서버에 해시로만 저장되어 이후 다시 조회할 수 없습니다.
                    """
    )
    @PostMapping("/{cctvId}/device-token")
    public ResponseEntity<ApiResponse<DeviceTokenIssueResponse>> issueDeviceToken(
            @PathVariable UUID cctvId
    ) {
        return ResponseEntity.ok(ApiResponse.success(cctvService.issueDeviceToken(cctvId)));
    }

    @Operation(
            summary = "CCTV 목록 조회",
            description = """
                    요청자 소속 학교의 CCTV 목록을 조회합니다. 활성/비활성 여부와 관계없이
                    모두 포함되며, enabled 필드로 구분해야 합니다.

                    floorId를 지정하면 해당 층의 CCTV만 반환하고, 생략하면 학교 전체 CCTV를
                    반환합니다. 각 항목에는 감시 영역(monitoredGridCellCount, monitoredAreaM2)이
                    함께 포함되며, 층에 그리드가 아직 설정되지 않은 경우 gridCellSizeMeter와
                    monitoredAreaM2는 null입니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<CctvResponse>>> getCctvs(
            @RequestParam(required = false) UUID floorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_LIST_FOUND,
                cctvService.getCctvs(floorId, authentication.getName())
        ));
    }

    @Operation(
            summary = "CCTV 상세 조회",
            description = """
                    CCTV 한 건의 상세 정보와 감시 GridCell 목록을 조회합니다. 요청자와 다른
                    학교 소속 CCTV는 조회할 수 없고 찾을 수 없음으로 처리됩니다.

                    gridCells는 행(rowIndex) 오름차순, 같은 행에서는 열(columnIndex) 오름차순으로
                    정렬되어 반환됩니다. 아직 감시 영역이 설정되지 않은 CCTV는 gridCells가
                    빈 배열로 반환됩니다.
                    """
    )
    @GetMapping("/{cctvId}")
    public ResponseEntity<ApiResponse<CctvResponse>> getCctv(
            @PathVariable UUID cctvId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_DETAIL_FOUND,
                cctvService.getCctv(cctvId, authentication.getName())
        ));
    }

    @Operation(
            summary = "CCTV 감시 GridCell 조회",
            description = """
                    CCTV 한 건에 매핑된 감시 GridCell 목록을 조회합니다. 내부적으로 CCTV 상세
                    조회(GET /{cctvId})와 동일한 응답을 반환하므로, 감시 영역만 필요한 화면에서도
                    CCTV 기본 정보가 함께 내려옵니다.

                    gridCells는 행(rowIndex) 오름차순, 같은 행에서는 열(columnIndex) 오름차순으로
                    정렬됩니다. 아직 설정되지 않았다면 빈 배열입니다.
                    """
    )
    @GetMapping("/{cctvId}/grid-cells")
    public ResponseEntity<ApiResponse<CctvResponse>> getGridCells(
            @PathVariable UUID cctvId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_GRID_CELLS_FOUND,
                cctvService.getGridCells(cctvId, authentication.getName())
        ));
    }

    @Operation(
            summary = "CCTV 감시 GridCell 설정",
            description = """
                    CCTV가 감시하는 GridCell 목록을 새 목록으로 전체 교체합니다. 기존 매핑은
                    모두 삭제되고 요청에 담긴 gridCellIds로 다시 채워지므로, 부분 추가/삭제가
                    아니라 항상 전체 목록을 보내야 합니다.

                    설정이 성공하면 혼잡도 설정 버전이 증가합니다 - 이 버전은 혼잡도 계산
                    로직이 그리드-엣지 매핑 변경을 감지해 캐시를 무효화하는 데 쓰이므로,
                    프론트에서 별도로 처리할 값은 아닙니다.

                    CCTV가 속한 층에 그리드가 설정되어 있지 않으면 실패합니다. gridCellIds는
                    비어 있을 수 없고, 중복되거나 해당 층 소속이 아니거나 보행 불가능
                    (walkable=false) 셀이 섞여 있으면 실패합니다.
                    """
    )
    @PutMapping("/{cctvId}/grid-cells")
    public ResponseEntity<ApiResponse<CctvResponse>> configureGridCells(
            @PathVariable UUID cctvId,
            @Valid @RequestBody ConfigureCctvGridCellsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_GRID_CELLS_CONFIGURED,
                cctvService.configureGridCells(cctvId, request, authentication.getName())
        ));
    }

    @Operation(
            summary = "CCTV 이름/위치 수정",
            description = """
                    CCTV의 이름과 도면상 좌표(x, y)를 수정합니다. code, 감시 GridCell,
                    활성화 여부, 디바이스 토큰에는 영향을 주지 않습니다.

                    x, y는 0.0~1.0 범위의 상대 좌표로, 도면 이미지 기준 비율 좌표입니다.
                    소속 층은 이 API로 바꿀 수 없습니다.
                    """
    )
    @PatchMapping("/{cctvId}")
    public ResponseEntity<ApiResponse<CctvResponse>> updateCctv(
            @PathVariable UUID cctvId,
            @Valid @RequestBody UpdateCctvRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_UPDATED,
                cctvService.updateCctv(cctvId, request, authentication.getName())
        ));
    }

    @Operation(
            summary = "CCTV 활성화",
            description = """
                    비활성화된 CCTV를 다시 활성화합니다. 이미 활성 상태여도 에러 없이
                    그대로 성공 처리됩니다(멱등).

                    비활성 상태에서는 해당 CCTV(Pi)가 유도등 명령 폴링(GET
                    /api/v1/device/light-commands) 시 CCTV_DISABLED로 거부되므로,
                    현장 점검이 끝난 CCTV를 다시 운영에 투입할 때 사용합니다.
                    """
    )
    @PatchMapping("/{cctvId}/enable")
    public ResponseEntity<ApiResponse<CctvResponse>> enableCctv(
            @PathVariable UUID cctvId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_ENABLED,
                cctvService.enableCctv(cctvId, authentication.getName())
        ));
    }

    @Operation(
            summary = "CCTV 비활성화",
            description = """
                    CCTV를 비활성화합니다. 이미 비활성 상태여도 에러 없이 그대로 성공 처리됩니다
                    (멱등).

                    비활성화되면 해당 CCTV(Pi)는 디바이스 토큰이 유효해도 유도등 명령 폴링(GET
                    /api/v1/device/light-commands)이 CCTV_DISABLED로 거부됩니다 - 이 CCTV가
                    담당하는 유도등들은 새 명령을 더 이상 받아가지 못하게 됩니다. 하드웨어
                    점검/철거 시 사용합니다.
                    """
    )
    @PatchMapping("/{cctvId}/disable")
    public ResponseEntity<ApiResponse<CctvResponse>> disableCctv(
            @PathVariable UUID cctvId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_DISABLED,
                cctvService.disableCctv(cctvId, authentication.getName())
        ));
    }
}
