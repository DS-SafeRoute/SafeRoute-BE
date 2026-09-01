package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.response.CongestionConfigQueryResponse;
import com.saferoute.domain.congestion.service.CongestionConfigQueryService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혼잡도", description = "Pi 혼잡 설정 조회 API")
@RestController
@RequestMapping("/api/v1/device/congestion-config")
@RequiredArgsConstructor
@Validated
public class CongestionConfigController {

    private final DeviceAuthorizationService deviceAuthorizationService;
    private final CongestionConfigQueryService congestionConfigQueryService;

    @Operation(
            summary = "Pi 혼잡 설정 조회",
            description = """
                    Pi가 주기적으로 호출해 해당 CCTV에 적용할 혼잡 판정 설정을 조회합니다.

                    CCTV가 속한 건물에 RUNNING 상태 훈련 세션이 없으면 trainingActive가 false로
                    반환되며, 이 경우 trainingSessionId/monitoredAreaM2/snapshotIntervalSec/
                    targetInferenceFps/congestionThresholds/eventDetection은 모두 null입니다.
                    Pi는 이때 설정을 적용하지 않고 configVersion만으로 재시도 주기를 판단하면 됩니다.

                    훈련이 RUNNING 상태이면 trainingActive가 true가 되고, 해당 CCTV의 GridCell
                    개수와 층의 격자 한 칸 크기(m)로 계산한 감시 면적(monitoredAreaM2)과 함께
                    혼잡 임계값(congestionThresholds)·탐지 설정(eventDetection)이 채워집니다.

                    congestionThresholds의 CAUTION_FROM/CROWDED_FROM/VERY_CROWDED_FROM은
                    밀도(인원수/㎡) 기준 하한값이며, BE는 이 값 이상(>=)일 때 해당 단계로 판정합니다.
                    configVersion은 이 임계값들이나 CCTV 감시 영역(GridCell)이 바뀔 때마다
                    증가하는 값으로, Pi와 BE의 설정 동기화 여부를 판단하는 데 사용됩니다.
                    """
    )
    @GetMapping
    public ResponseEntity<CongestionConfigQueryResponse> getConfig(
            @AuthenticationPrincipal DevicePrincipal principal,
            @RequestParam @NotBlank String cctvCode
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, cctvCode);
        return ResponseEntity.ok(congestionConfigQueryService.getConfigFor(cctv));
    }
}
