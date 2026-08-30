package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.ReportCongestionEventRequest;
import com.saferoute.domain.congestion.dto.request.ConnectEventImageRequest;
import com.saferoute.domain.congestion.dto.response.CongestionEventResponse;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.domain.congestion.service.CongestionEventImageService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@Tag(name = "혼잡도", description = "Pi 즉시 혼잡 이벤트 수신 API")
@RestController
@RequestMapping("/api/v1/device/congestion-events")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionEventService congestionEventService;
    private final CongestionEventImageService congestionEventImageService;
    private final DeviceAuthorizationService deviceAuthorizationService;

    @Operation(
            summary = "혼잡 이벤트 즉시 수신",
            description = """
                    Pi가 혼잡 진입(CONGESTION_STARTED)/상승(CONGESTION_LEVEL_UP)/종료(CONGESTION_ENDED)를
                    감지한 순간 즉시 보내는 이벤트를 저장합니다. Pi가 함께 보낸 localDensity/
                    localCongestionLevel은 참고용일 뿐이며, 응답의 density/congestionLevel은 BE가
                    해당 CCTV의 감시 면적(GridCell 기반)과 headcount로 다시 계산한 값입니다.

                    eventId 기준으로 멱등 처리됩니다. 같은 eventId로 재전송하면 새로 만들지 않고
                    저장된 기존 이벤트를 그대로 반환하며, 최초 저장 시에만 201 Created, 이미 존재하면
                    200 OK로 응답합니다. eventId는 저장돼 있는데 trainingSessionId/cctvCode가 요청과
                    다르면 오류가 됩니다(다른 이벤트와 충돌).

                    congestionLevel이 CROWDED 이상이거나, CONGESTION_ENDED로 정상(NORMAL) 상태로
                    돌아온 경우 해당 CCTV가 감시하는 모든 경로 구간(MapEdge)에 대해 경로 재계산이
                    비동기로 트리거됩니다. 이 응답의 eventImageKey/imageUploadStatus는 아직 이미지가
                    연결되지 않은 상태(PENDING)로, 이후 PATCH /{eventId}/image로 채워집니다.
                    """
    )
    @PostMapping
    public ResponseEntity<CongestionEventResponse> reportCongestionEvent(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody ReportCongestionEventRequest request
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, request.cctvCode());
        IdempotentSaveResult<CongestionEventItem> saveResult =
                congestionEventService.reportCongestionEvent(cctv, request);
        CongestionEventResponse response = CongestionEventResponse.from(saveResult.item());
        HttpStatus status = saveResult.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(
            summary = "혼잡 이벤트 이미지 연결",
            description = """
                    Pi가 S3에 직접 업로드를 마친 혼잡 이벤트 이미지의 object key를 해당 이벤트에
                    연결합니다. 사전에 POST /api/v1/device/congestion-images/presigned-url로
                    발급받은 presigned URL로 이미지를 업로드한 뒤 이 API를 호출하는 흐름입니다.

                    대상 이벤트는 처리(PROCESSED)까지 끝난 상태여야 하며, eventImageKey는
                    "training/{trainingSessionId}/events/{cctvCode}/{eventId}.jpg" 형식이고
                    경로 안의 세션/CCTV/이벤트 식별자가 실제 이벤트와 일치해야 합니다. 형식이
                    다르거나 신원이 맞지 않으면 오류가 됩니다.

                    이미 같은 키·업로드 시각으로 완료(COMPLETED) 처리된 이벤트에 대한 재요청은
                    새로 처리하지 않고 그대로 성공(204)만 반환합니다(멱등). 이미 다른 이미지가
                    연결되어 있는 등 상태가 맞지 않으면 충돌 오류가 나며, S3에 실제로 해당 객체가
                    존재하지 않아도 오류가 됩니다. 성공 시 본문 없이 204 No Content를 반환하고,
                    관리자 화면에는 웹소켓으로 이미지 갱신 이벤트가 발행됩니다.
                    """
    )
    @PatchMapping("/{eventId}/image")
    public ResponseEntity<Void> connectEventImage(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable UUID eventId,
            @Valid @RequestBody ConnectEventImageRequest request
    ) {
        congestionEventImageService.connectImage(principal, eventId, request);
        return ResponseEntity.noContent().build();
    }
}
