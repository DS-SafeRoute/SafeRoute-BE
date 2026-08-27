package com.saferoute.domain.training.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// FireSpreadService는 RUNNING 세션을 순회하며 FireSpreadStepService(별도 빈)의 프록시를 통해
// spreadOneStep을 호출하는 역할만 한다. 같은 클래스 안에서 직접 호출하면 self-invocation으로
// @Transactional이 무시되므로, 반드시 다른 빈에 위임해야 한다 - 이 위임과 예외 격리만 검증한다.
@ExtendWith(MockitoExtension.class)
class FireSpreadServiceTest {

    @InjectMocks
    private FireSpreadService fireSpreadService;

    @Mock
    private TrainingSessionRepository sessionRepository;

    @Mock
    private FireSpreadStepService fireSpreadStepService;

    private TrainingSession runningSessionWithId(UUID id) {
        TrainingSession session = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now(), mock(User.class), mock(TrainingScenario.class));
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    @Test
    @DisplayName("RUNNING 세션마다 FireSpreadStepService의 프록시를 통해 spreadOneStep을 호출한다")
    void spreadAllRunningSessions_delegatesEachSessionToStepService() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        given(sessionRepository.findAllByStatus(TrainingStatus.RUNNING))
                .willReturn(List.of(runningSessionWithId(id1), runningSessionWithId(id2)));

        fireSpreadService.spreadAllRunningSessions();

        verify(fireSpreadStepService).spreadOneStep(id1);
        verify(fireSpreadStepService).spreadOneStep(id2);
    }

    @Test
    @DisplayName("한 세션의 확산 처리가 실패해도 다른 세션 처리는 계속된다")
    void spreadAllRunningSessions_oneSessionFails_othersStillProcessed() {
        UUID failingId = UUID.randomUUID();
        UUID okId = UUID.randomUUID();
        given(sessionRepository.findAllByStatus(TrainingStatus.RUNNING))
                .willReturn(List.of(runningSessionWithId(failingId), runningSessionWithId(okId)));
        doThrow(new RuntimeException("boom")).when(fireSpreadStepService).spreadOneStep(failingId);

        fireSpreadService.spreadAllRunningSessions();

        verify(fireSpreadStepService).spreadOneStep(failingId);
        verify(fireSpreadStepService).spreadOneStep(okId);
    }
}
