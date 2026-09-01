package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.AssignCctvRequest;
import com.saferoute.domain.device.dto.request.ChangeLightDirectionRequest;
import com.saferoute.domain.device.dto.request.ConfigureGuidanceRequest;
import com.saferoute.domain.device.dto.request.CreateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdateIoTLightRequest;
import com.saferoute.domain.device.dto.request.UpdatePiEndpointRequest;
import com.saferoute.domain.device.dto.response.IoTLightResponse;
import com.saferoute.domain.device.dto.response.LightDirectionResponse;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.IoTLightSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IoT 유도등", description = "IoT 유도등 등록/조회/방향 제어 API")
@RestController
@RequestMapping("/api/v1/lights")
@RequiredArgsConstructor
public class IoTLightController {

    private final IoTLightService iotLightService;

    @Operation(
            summary = "IoT 유도등 등록",
            description = """
                    도면 위치(floorId, x, y)만으로 IoT 유도등을 새로 등록합니다. code는
                    클라이언트가 보내지 않으며 서버가 LIGHT_001 형식으로 자동 채번합니다.

                    등록 시점에는 분기 정보(decisionNode/leftEdge/rightEdge), piEndpoint,
                    담당 CCTV가 모두 비어 있어 guidanceConfigured는 false, piEndpoint와
                    cctvId는 null로 반환됩니다. 실제로 방향 전환 명령을 받으려면 경로 안내
                    설정(PATCH /{lightId}/guidance)과 담당 CCTV 지정(PATCH /{lightId}/cctv)을
                    별도로 완료해야 합니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<IoTLightResponse>> createLight(
            @Valid @RequestBody CreateIoTLightRequest request,
            Authentication authentication
    ) {
        IoTLightResponse response = iotLightService.createLight(request, authentication.getName());
        return ResponseEntity.status(IoTLightSuccessCode.IOT_LIGHT_CREATED.getHttpStatus())
                .body(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_CREATED, response));
    }

    @Operation(
            summary = "IoT 유도등 목록 조회",
            description = """
                    요청자 소속 학교의 IoT 유도등 목록을 조회합니다. 활성/비활성 여부와
                    관계없이 모두 포함되며, enabled 필드로 구분해야 합니다.

                    floorId를 지정하면 해당 층의 유도등만 반환하고, 생략하면 학교 전체
                    유도등을 반환합니다. 현재 점등 방향(LEFT/RIGHT/BOTH/OFF)은 훈련별로
                    바뀌는 동적 상태라 이 응답에 포함되지 않습니다 - 서버 메모리에서만
                    관리되며 별도 계약이 없어 이 API로는 조회할 수 없습니다.

                    decisionNodeId/leftEdgeId/rightEdgeId, piEndpoint, cctvId는 아직
                    설정되지 않았으면 null이며, guidanceConfigured가 true여야 방향 전환이
                    가능합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<IoTLightResponse>>> getLights(
            @RequestParam(required = false) UUID floorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_LIST_FOUND,
                        iotLightService.getLights(floorId, authentication.getName())));
    }

    @Operation(
            summary = "IoT 유도등 상세 조회",
            description = """
                    IoT 유도등 한 건의 상세 정보를 조회합니다. 요청자와 다른 학교 소속
                    유도등은 조회할 수 없고 찾을 수 없음으로 처리됩니다.

                    현재 점등 방향은 훈련별 동적 상태(서버 메모리)라 이 응답에 포함되지
                    않습니다. decisionNodeId/leftEdgeId/rightEdgeId, piEndpoint, cctvId는
                    아직 설정되지 않았으면 null입니다.
                    """
    )
    @GetMapping("/{lightId}")
    public ResponseEntity<ApiResponse<IoTLightResponse>> getLight(
            @PathVariable UUID lightId,
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DETAIL_FOUND,
                        iotLightService.getLight(lightId, authentication.getName())));
    }

    @Operation(
            summary = "IoT 유도등 경로 안내 설정",
            description = """
                    유도등이 방향을 판단하는 분기 노드(decisionNodeId)와 좌/우 점등 시
                    안내되는 통로 엣지(leftEdgeId, rightEdgeId)를 설정합니다. 설정이 끝나면
                    guidanceConfigured가 true가 되어야 OFF 이외의 방향(LEFT/RIGHT/BOTH)으로
                    전환할 수 있습니다.

                    leftEdgeId와 rightEdgeId는 서로 같을 수 없고, 둘 다 decisionNodeId에
                    실제로 연결된 엣지여야 합니다 - 아니면 요청이 거부됩니다.

                    이 API는 기존 설정을 완전히 새 값으로 덮어씁니다.
                    """
    )
    @PatchMapping("/{lightId}/guidance")
    public ResponseEntity<ApiResponse<IoTLightResponse>> configureGuidance(
            @PathVariable UUID lightId,
            @Valid @RequestBody ConfigureGuidanceRequest request,
            Authentication authentication
    ) {
        IoTLightResponse response = iotLightService.configureGuidance(lightId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_GUIDANCE_CONFIGURED, response));
    }

    @Operation(
            summary = "IoT 유도등 이름/위치 수정",
            description = """
                    유도등의 이름과 도면상 좌표(x, y)를 수정합니다. code, 경로 안내 설정,
                    piEndpoint, 담당 CCTV, 활성화 여부에는 영향을 주지 않습니다.

                    소속 층은 이 API로 바꿀 수 없습니다.
                    """
    )
    @PatchMapping("/{lightId}")
    public ResponseEntity<ApiResponse<IoTLightResponse>> updateLight(
            @PathVariable UUID lightId,
            @Valid @RequestBody UpdateIoTLightRequest request,
            Authentication authentication
    ) {
        IoTLightResponse response = iotLightService.updateLight(lightId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_UPDATED, response));
    }

    @Operation(
            summary = "IoT 유도등 점등 방향 전환",
            description = """
                    유도등의 점등 방향을 전환합니다. EC2 서버가 사설망의 라즈베리파이를 직접
                    호출할 수 없어, 이 API는 Pi를 즉시 부르지 않고 LightCommand를 PENDING
                    상태로 큐에 적재하기만 합니다. 실제 릴레이 제어는 이 유도등을 담당하는
                    CCTV(Pi)가 GET /api/v1/device/light-commands로 주기적으로 폴링해가서
                    수행하고, 결과는 Pi가 PATCH /api/v1/device/light-commands/{commandId}/ack로
                    비동기 보고합니다.

                    응답의 direction/changedAt은 명령을 큐에 적재하는 시점에 낙관적으로
                    확정되는 값입니다 - Pi가 실제로 명령을 폴링해가서 실행에 성공했는지,
                    실패했는지, 아예 오프라인이라 15초 안에 ACK를 못 보내 타임아웃되는지는
                    이 응답만으로는 알 수 없습니다. 실행 결과가 필요하면 유도등 명령 폴링/ACK
                    조회 경로를 별도로 확인해야 합니다.

                    같은 유도등에 이미 PENDING 상태로 쌓여 있던(아직 Pi가 가져가지 않은) 이전
                    명령은 이번 요청으로 즉시 SUPERSEDED 처리되어 실행되지 않습니다 - Pi는
                    폴링 시 유도등당 최신 PENDING 명령 하나만 가져가므로, 밀린 방향 전환이
                    뒤늦게 실행되는 일은 없습니다.

                    direction이 OFF가 아니면 유도등에 경로 안내(guidance)가 먼저 설정되어
                    있어야 하고, 담당 CCTV(cctv)가 지정되어 있어야 합니다. 비활성화된
                    유도등이거나 두 조건 중 하나라도 충족하지 못하면 요청이 거부됩니다.
                    """
    )
    @PatchMapping("/{lightId}/direction")
    public ResponseEntity<ApiResponse<LightDirectionResponse>> changeDirection(
            @PathVariable UUID lightId,
            @Valid @RequestBody ChangeLightDirectionRequest request,
            Authentication authentication
    ) {
        LightDirectionResponse response = iotLightService.changeDirection(lightId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DIRECTION_CHANGED, response));
    }

    @Operation(
            summary = "IoT 유도등 Pi 엔드포인트 설정",
            description = """
                    유도등의 릴레이를 직접 제어하는 라즈베리파이 주소(piEndpoint, 예:
                    http://192.168.0.50:5000)를 등록/수정합니다. 등록 시점에는 비어 있을 수
                    있어, 하드웨어 배치가 끝난 뒤 별도로 설정하는 용도입니다.

                    참고로 유도등 명령 폴링/ACK 인증은 이 piEndpoint가 아니라 담당 CCTV(PATCH
                    /{lightId}/cctv로 지정)의 디바이스 토큰을 사용합니다 - piEndpoint는 현재
                    참고용 메타데이터이며 실제 명령 전달 경로에는 사용되지 않습니다.
                    """
    )
    @PatchMapping("/{lightId}/pi-endpoint")
    public ResponseEntity<ApiResponse<IoTLightResponse>> updatePiEndpoint(
            @PathVariable UUID lightId,
            @Valid @RequestBody UpdatePiEndpointRequest request,
            Authentication authentication
    ) {
        IoTLightResponse response = iotLightService.updatePiEndpoint(lightId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_PI_ENDPOINT_UPDATED, response));
    }

    @Operation(
            summary = "IoT 유도등 담당 CCTV 연결",
            description = """
                    유도등의 릴레이를 실제로 제어하는 라즈베리파이를 지정합니다. 릴레이 자체는
                    별도 네트워크 장비이지만, 그 릴레이에 명령을 보내는 코드는 혼잡도 감지용
                    Pi 프로세스 안에서 함께 돌기 때문에, 유도등을 직접 지정하지 않고 그 Pi가
                    맡고 있는 CCTV를 통해 연결합니다.

                    이 연결이 곧 유도등 명령 폴링(GET /api/v1/device/light-commands?cctvCode=...)
                    의 인증/라우팅 기준입니다 - 지정된 CCTV(Pi)가 자신의 디바이스 토큰으로
                    폴링해야만 이 유도등의 PENDING 명령을 가져갈 수 있습니다. 담당 CCTV가
                    지정되지 않은 유도등은 점등 방향 전환 자체가 거부됩니다.

                    이 API는 기존 연결을 새 CCTV로 완전히 교체합니다. 지정하는 CCTV는 요청자와
                    같은 학교 소속이어야 합니다.
                    """
    )
    @PatchMapping("/{lightId}/cctv")
    public ResponseEntity<ApiResponse<IoTLightResponse>> assignCctv(
            @PathVariable UUID lightId,
            @Valid @RequestBody AssignCctvRequest request,
            Authentication authentication
    ) {
        IoTLightResponse response = iotLightService.assignCctv(lightId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_CCTV_ASSIGNED, response));
    }

    @Operation(
            summary = "IoT 유도등 활성화",
            description = """
                    비활성화된 유도등을 다시 활성화합니다. 이미 활성 상태여도 에러 없이
                    그대로 성공 처리됩니다(멱등).

                    비활성 상태에서는 점등 방향 전환(PATCH /{lightId}/direction) 요청 자체가
                    거부되므로, 현장 점검이 끝난 유도등을 다시 운영에 투입할 때 사용합니다.
                    """
    )
    @PatchMapping("/{lightId}/enable")
    public ResponseEntity<ApiResponse<IoTLightResponse>> enableLight(
            @PathVariable UUID lightId,
            Authentication authentication) {
        IoTLightResponse response = iotLightService.enableLight(lightId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_ENABLED, response));
    }

    @Operation(
            summary = "IoT 유도등 비활성화",
            description = """
                    유도등을 비활성화합니다. 이미 비활성 상태여도 에러 없이 그대로 성공
                    처리됩니다(멱등).

                    비활성화되면 점등 방향 전환(PATCH /{lightId}/direction) 요청이 거부되어
                    새 명령이 큐에 적재되지 않습니다. 단, 이미 적재되어 Pi가 폴링해간 명령의
                    실행이나 ACK 처리 자체를 막지는 않습니다. 하드웨어 점검/철거 시 사용합니다.
                    """
    )
    @PatchMapping("/{lightId}/disable")
    public ResponseEntity<ApiResponse<IoTLightResponse>> disableLight(
            @PathVariable UUID lightId,
            Authentication authentication) {
        IoTLightResponse response = iotLightService.disableLight(lightId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DISABLED, response));
    }

    @Operation(
            summary = "IoT 유도등 삭제",
            description = """
                    IoT 유도등을 영구 삭제합니다. 유도등에 연결된 도면 노드(customNode)를
                    지우는 방식으로 동작하며, DB의 ON DELETE CASCADE 설정에 의해 유도등
                    레코드 자체도 함께 삭제됩니다.

                    노드를 지우기 전에 그 노드와 연결된 통로 엣지(from/to 어느 쪽이든)를
                    먼저 모두 삭제합니다. 복구할 수 없는 삭제이므로 되돌릴 수 없습니다.
                    """
    )
    @DeleteMapping("/{lightId}")
    public ResponseEntity<ApiResponse<Void>> deleteLight(
            @PathVariable UUID lightId,
            Authentication authentication) {
        iotLightService.deleteLight(lightId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(IoTLightSuccessCode.IOT_LIGHT_DELETED, null));
    }
}
