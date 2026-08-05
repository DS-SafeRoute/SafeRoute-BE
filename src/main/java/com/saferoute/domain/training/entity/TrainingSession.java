package com.saferoute.domain.training.entity;

import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "training_sessions")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingSession {

  @Id
  @GeneratedValue
  private UUID id;

  @Version
  private Long version;

  //훈련 상태 (RUNNING, STOPPED 등)
  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "training_status", nullable = false, length = 20)
  private TrainingStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "act_participants")
  private Integer actualParticipants;

  @Column(name = "survival_rate")
  private BigDecimal currentSurvivalRate;

  @Column(name = "avg_evacuation_sec")
  private Integer currentAvgEvacuationSec;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User admin;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "training_scenario_id", nullable = false)
  private TrainingScenario scenario;

  @OneToOne(mappedBy = "trainingSession", cascade = CascadeType.ALL)
  private TrainingReport trainingReport;

  private TrainingSession(TrainingStatus status, Instant startedAt, User admin, TrainingScenario scenario) {
    this.status = status;
    this.startedAt = startedAt;
    this.admin = admin;
    this.scenario = scenario;
  }

  // 훈련 세션 생성용 정적 팩토리 메서드
  public static TrainingSession create(TrainingStatus status, Instant startedAt, User admin, TrainingScenario scenario) {
    return new TrainingSession(status, startedAt, admin, scenario);
  }

  // 관리자가 훈련 시작 버튼을 누른 시각으로 실제 시작 시각을 갱신하며 RUNNING으로 전이한다.
  public void start(Instant startedAt) {
    this.status = TrainingStatus.RUNNING;
    this.startedAt = startedAt;
  }

  // 훈련 정상 종료
  public void complete(Instant endedAt) {
    this.status = TrainingStatus.COMPLETED;
    this.endedAt = endedAt;
  }

  // 관리자에 의한 강제 종료
  public void stop(Instant endedAt) {
    this.status = TrainingStatus.STOPPED;
    this.endedAt = endedAt;
  }

  public void fail(Instant endedAt) {
    this.status = TrainingStatus.FAILED;
    this.endedAt = endedAt;
  }
}