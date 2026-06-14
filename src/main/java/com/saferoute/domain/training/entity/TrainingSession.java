package com.saferoute.domain.training.entity;

import com.saferoute.domain.training.TrainingStatus;
import com.saferoute.domain.user.entity.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
public class TrainingSession {

  @Id
  @GeneratedValue
  private UUID id;

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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User admin;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "training_scenario_id", nullable = false)
  private TrainingScenario scenario;

  @OneToOne(mappedBy = "trainingSession", cascade = CascadeType.ALL)
  private TrainingReport trainingReport;

  public static TrainingSession create(TrainingStatus status, Instant startedAt, User admin, TrainingScenario scenario) {
    TrainingSession session = new TrainingSession();
    session.status = status;
    session.startedAt = startedAt;
    session.admin = admin;
    session.scenario = scenario;
    return session;
  }
}