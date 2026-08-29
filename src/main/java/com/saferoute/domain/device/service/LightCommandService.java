package com.saferoute.domain.device.service;

import com.saferoute.domain.device.dto.request.AckLightCommandRequest;
import com.saferoute.domain.device.dto.response.LightCommandAckResponse;
import com.saferoute.domain.device.dto.response.LightCommandListResponse;
import com.saferoute.domain.device.dto.response.LightCommandResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.LightCommand;
import com.saferoute.domain.device.entity.LightCommandStatus;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.device.repository.LightCommandJpaRepository;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// BE는 Pi를 직접 호출하지 않고(EC2->사설 Pi 직접 호출 금지) 명령을 큐에
// 적재만 하면, 같은 Pi가 이미 갖고 있는 CCTV 디바이스 토큰으로 폴링해가서 실행하고
// ACK로 결과를 보고하는 구조. IoTLightService는 이 서비스를 통해 명령을 적재한다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LightCommandService {

    // Pi가 짧은 주기로 폴링해가는 명령이라 훈련 세션 타임아웃(10분)보다 훨씬 짧게 잡는다.
    // 이 시간 안에 ACK가 없으면 Pi가 오프라인이거나 릴레이 통신에 실패한 것으로 본다.
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(15);

    private final IoTLightJpaRepository iotLightJpaRepository;
    private final LightCommandJpaRepository lightCommandJpaRepository;

    // Pi가 폴링할 때, 그 CCTV(=그 Pi)가 담당하는 유도등들의 최신 PENDING 명령을 SENT로
    // 전환하며 가져간다. 유도등 하나당 최신 명령 1개만 반환한다 - 중간에 밀린 명령은
    // 어차피 최신 방향만 의미가 있으므로 SUPERSEDED로 남겨두고 무시한다.
    @Transactional
    public LightCommandListResponse pollCommands(Cctv cctv) {
        List<IoTLight> lights = iotLightJpaRepository.findAllByCctv_Id(cctv.getId());
        Instant now = Instant.now();

        List<LightCommandResponse> responses = new ArrayList<>();
        for (IoTLight light : lights) {
            lightCommandJpaRepository
                    .findFirstByLight_IdAndStatusOrderByCreatedAtDesc(light.getId(), LightCommandStatus.PENDING)
                    .ifPresent(command -> {
                        command.markSent(now);
                        responses.add(LightCommandResponse.from(command));
                    });
        }
        return new LightCommandListResponse(responses);
    }

    // Pi가 실행 결과를 보고할 때 호출한다. 이미 ACKED/FAILED/TIMED_OUT으로 처리된
    // 명령이면 아무 것도 바꾸지 않고 그대로 반환한다 - Pi가 응답을 못 받아 같은
    // commandId로 재전송해도 중복 처리(멱등)되지 않는다.
    @Transactional
    public LightCommandAckResponse ack(UUID commandId, DevicePrincipal principal, AckLightCommandRequest request) {
        LightCommand command = lightCommandJpaRepository.findById(commandId)
                .orElseThrow(() -> new ApiException(IoTLightErrorCode.LIGHT_COMMAND_NOT_FOUND));

        Cctv owningCctv = command.getLight().getCctv();
        if (owningCctv == null || !owningCctv.getId().equals(principal.cctvId())) {
            throw new ApiException(IoTLightErrorCode.LIGHT_COMMAND_CCTV_MISMATCH);
        }

        if (command.getStatus() == LightCommandStatus.SENT) {
            if (Boolean.TRUE.equals(request.success())) {
                command.ack(Instant.now());
            } else {
                command.fail(Instant.now(), request.failReason());
            }
        }
        return new LightCommandAckResponse(command.getId(), command.getStatus());
    }

    // SENT 상태로 ACK_TIMEOUT을 넘긴 명령을 주기적으로 스캔해 TIMED_OUT 처리한다
    // (TrainingTimeoutScheduler.failTimedOutSessions()와 동일한 컨벤션).
    // Pi가 오프라인이거나 릴레이 통신에 실패해 ACK를 못 보낸 경우, 이 명령이 영원히
    // SENT로 남아 있지 않도록 하는 안전장치다.
    @Transactional
    public void timeoutStaleCommands() {
        Instant threshold = Instant.now().minus(ACK_TIMEOUT);
        List<LightCommand> staleCommands = lightCommandJpaRepository
                .findAllByStatusAndSentAtBefore(LightCommandStatus.SENT, threshold);

        for (LightCommand command : staleCommands) {
            command.timeout();
            log.warn("유도등 명령 ACK 타임아웃: commandId={}, lightId={}, direction={}",
                    command.getId(), command.getLight().getId(), command.getDirection());
        }
    }
}
