package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.LightCommand;
import com.saferoute.domain.device.entity.LightCommandStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LightCommandJpaRepository extends JpaRepository<LightCommand, UUID> {

    // Pi가 폴링할 때 그 유도등의 최신 PENDING 명령 하나만 가져간다 - 중간에 밀린
    // 명령은 어차피 최신 방향만 의미가 있으므로 버린다.
    Optional<LightCommand> findFirstByLight_IdAndStatusOrderByCreatedAtDesc(
            UUID lightId, LightCommandStatus status);

    // 같은 유도등에 더 최신 명령을 적재하기 전에, 기존 PENDING 명령들을 SUPERSEDED 처리하기 위한 조회
    List<LightCommand> findAllByLight_IdAndStatus(UUID lightId, LightCommandStatus status);

    // 타임아웃 스케줄러가 SENT 상태로 일정 시간 지난 명령을 훑을 때 사용
    List<LightCommand> findAllByStatusAndSentAtBefore(LightCommandStatus status, Instant threshold);
}
