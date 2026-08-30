package com.saferoute.domain.device.scheduler;

import com.saferoute.domain.device.service.LightCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// SENT 상태로 ACK_TIMEOUT(LightCommandService 참고)을 넘긴 유도등 명령을 주기적으로
// 스캔해 TIMED_OUT 처리한다. 명령 자체가 짧은 주기로 도는 만큼 스캔 주기도 촘촘하게 잡는다.
@Component
@RequiredArgsConstructor
public class LightCommandTimeoutScheduler {

    private static final long SCAN_INTERVAL_MS = 5_000;

    private final LightCommandService lightCommandService;

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS)
    public void scanStaleCommands() {
        lightCommandService.timeoutStaleCommands();
    }
}
