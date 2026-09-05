package com.saferoute.domain.device.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// BE가 Pi에 직접 호출하지 않고(EC2->사설 Pi 직접 호출 금지) Pi가 폴링해가는
// 명령 큐. IoTLightService가 방향을 바꿔야 할 때 PENDING으로 적재만 하고, Pi가
// GET /api/v1/device/light-commands로 가져가서 실행한 뒤 ACK로 결과를 보고한다.
//
// 상태 전이: PENDING -> SENT(Pi가 폴링) -> ACKED/FAILED(Pi가 보고)
//                   SENT -> TIMED_OUT(스케줄러가 일정 시간 ACK 없으면 처리)
//                   PENDING -> SUPERSEDED(같은 유도등에 더 최신 명령이 적재됨)
// 상태 전이 검증은 이 엔티티가 아니라 Service가 담당한다
// (RouteRecalculation, IoTLightService와 동일한 컨벤션).
@Entity
@Getter
@Table(name = "light_commands")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LightCommand {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "light_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private IoTLight light;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private IoTLightDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LightCommandStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    private LightCommand(IoTLight light, IoTLightDirection direction) {
        this.light = light;
        this.direction = direction;
        this.status = LightCommandStatus.PENDING;
    }

    // BE가 유도등 방향을 바꿔야 할 때 큐에 적재하는 정적 팩토리 메서드
    public static LightCommand createPending(IoTLight light, IoTLightDirection direction) {
        return new LightCommand(light, direction);
    }

    // Pi가 폴링해가는 시점에 호출한다.
    public void markSent(Instant sentAt) {
        this.status = LightCommandStatus.SENT;
        this.sentAt = sentAt;
    }

    // Pi가 실행 성공을 ACK로 보고했을 때 호출한다.
    public void ack(Instant ackedAt) {
        this.status = LightCommandStatus.ACKED;
        this.ackedAt = ackedAt;
    }

    // Pi가 실행 실패를 ACK로 보고했을 때 호출한다.
    public void fail(Instant ackedAt, String reason) {
        this.status = LightCommandStatus.FAILED;
        this.ackedAt = ackedAt;
        this.failReason = reason;
    }

    // SENT 상태로 일정 시간 ACK가 없을 때 타임아웃 스케줄러가 호출한다.
    public void timeout() {
        this.status = LightCommandStatus.TIMED_OUT;
    }

    // 같은 유도등에 더 최신 명령이 적재되어 이 PENDING 명령이 더 이상 의미가 없을 때 호출한다.
    public void supersede() {
        this.status = LightCommandStatus.SUPERSEDED;
    }
}
