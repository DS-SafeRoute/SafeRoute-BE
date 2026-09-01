package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.AckLightCommandRequest;
import com.saferoute.domain.device.dto.response.LightCommandAckResponse;
import com.saferoute.domain.device.dto.response.LightCommandListResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.device.service.LightCommandService;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유도등 명령", description = "Pi 유도등 명령 폴링/ACK API")
@RestController
@RequestMapping("/api/v1/device/light-commands")
@RequiredArgsConstructor
@Validated
public class LightCommandController {

    private final DeviceAuthorizationService deviceAuthorizationService;
    private final LightCommandService lightCommandService;

    @Operation(
            summary = "유도등 명령 폴링",
            description = """
                    Pi가 자신이 담당하는 CCTV(cctvCode)에 연결된 모든 유도등을 대상으로,
                    아직 가져가지 않은 최신 명령을 가져갑니다. EC2 서버가 사설망의 Pi를 직접
                    호출할 수 없어 만든 구조로, Pi가 이 API를 짧은 주기로 반복 호출(폴링)해
                    실행할 명령이 있는지 확인해야 합니다.

                    유도등 하나당 PENDING 상태의 최신 명령 1건만 반환하며, 반환과 동시에
                    해당 명령은 SENT 상태로 전환됩니다 - 같은 명령이 다음 폴링에서 다시
                    반환되지는 않습니다. 실행할 명령이 없는 유도등은 목록에 포함되지 않으므로,
                    담당 유도등이 있어도 commands가 빈 배열일 수 있습니다.

                    인증은 디바이스 토큰(Authorization)과 cctvCode가 그 토큰이 발급된 CCTV와
                    일치해야 하며, 해당 CCTV가 비활성화(disabled) 상태면 거부됩니다. 명령을
                    가져간 뒤에는 반드시 PATCH /{commandId}/ack로 실행 결과를 보고해야 합니다 -
                    15초 안에 ACK가 없으면 서버가 TIMED_OUT으로 처리합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<LightCommandListResponse> pollCommands(
            @AuthenticationPrincipal DevicePrincipal principal,
            @RequestParam @NotBlank String cctvCode
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, cctvCode);
        return ResponseEntity.ok(lightCommandService.pollCommands(cctv));
    }

    @Operation(
            summary = "유도등 명령 실행 결과 보고(ACK)",
            description = """
                    Pi가 폴링해간 명령의 실행 결과를 보고합니다. success=true면 명령이 ACKED로,
                    false면 FAILED로 전환되며 이때 failReason을 함께 남길 수 있습니다.

                    이 API는 명령이 SENT 상태(폴링해가서 아직 결과 보고 전)일 때만 실제로
                    상태를 바꿉니다. 이미 ACKED/FAILED/TIMED_OUT으로 처리된 명령에 다시
                    호출해도 상태는 바뀌지 않고 현재 상태를 그대로 응답합니다 - Pi가 응답을
                    받지 못해 같은 commandId로 재전송하더라도 중복 처리되지 않는 멱등 처리입니다.

                    명령을 폴링해간 뒤 15초 안에 ACK를 보내지 않으면, 서버 스케줄러가 먼저
                    TIMED_OUT으로 처리할 수 있습니다 - 이 경우 뒤늦게 도착한 ACK는 위와 같은
                    이유로 무시됩니다. ACK를 보내는 Pi(디바이스 토큰의 소유 CCTV)는 해당
                    명령이 속한 유도등의 담당 CCTV와 일치해야 합니다.
                    """
    )
    @PatchMapping("/{commandId}/ack")
    public ResponseEntity<LightCommandAckResponse> ack(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable UUID commandId,
            @Valid @RequestBody AckLightCommandRequest request
    ) {
        return ResponseEntity.ok(lightCommandService.ack(commandId, principal, request));
    }
}
