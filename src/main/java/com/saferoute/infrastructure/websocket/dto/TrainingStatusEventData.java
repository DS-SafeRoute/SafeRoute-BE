package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import java.time.Instant;

public record TrainingStatusEventData(
        TrainingStatus status,
        Instant startedAt,
        Instant endedAt
) {

    public static TrainingStatusEventData from(TrainingSession session) {
        return new TrainingStatusEventData(
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }
}