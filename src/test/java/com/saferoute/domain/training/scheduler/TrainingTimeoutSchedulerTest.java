package com.saferoute.domain.training.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.training.service.TrainingSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainingTimeoutSchedulerTest {

    @InjectMocks
    private TrainingTimeoutScheduler scheduler;

    @Mock
    private TrainingSessionService trainingSessionService;

    @Test
    @DisplayName("스캔 주기마다 타임아웃 세션 처리를 위임한다")
    void scanTimedOutSessions_delegatesToService() {
        scheduler.scanTimedOutSessions();

        verify(trainingSessionService, times(1)).failTimedOutSessions();
    }
}
