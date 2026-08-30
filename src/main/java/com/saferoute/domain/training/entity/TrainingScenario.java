package com.saferoute.domain.training.entity;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.user.entity.User;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "training_scenarios")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingScenario {

    @Id
    @GeneratedValue
    private UUID id;

    @NotBlank
    @Size(min = 2, max = 20)
    @Column(nullable = false, length = 20)
    private String name;

    @NotNull
    @Column(name = "exp_participants", nullable = false)
    private Integer expectedParticipants;

    // 훈련 리포트의 "총 대피 시간" 항목 점수를 매길 때 기준이 되는 목표 대피 시간(초).
    // 건물 규모/층수마다 적정 대피시간이 달라 고정값이 아니라 시나리오별로 관리자가 지정한다.
    @NotNull
    @Column(name = "target_evacuation_sec", nullable = false)
    private Integer targetEvacuationSec;

    @NotNull
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "is_template", nullable = false)
    private Boolean isTemplate = false;

    // 연결된 세션의 생명주기에 따라 갱신되는 진행 상태 (상태 전이 가드는 TrainingSessionService가 담당).
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScenarioStatus status;

    // 화재 확산 속도. 발화점(FireZone)마다가 아니라 시나리오 전체에 하나로 적용된다.
    // 확산 시뮬레이션의 tick 간격을 결정한다. (FAST < MEDIUM < SLOW 순으로 주기가 짧음)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "fire_spread_speed", nullable = false, length = 10)
    private FireSpreadSpeed fireSpreadSpeed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    // 훈련 시작 시 최초 대피 경로 계산의 출발 노드. 기존 시나리오는 값이 없을 수 있어 DB 컬럼은
    // nullable로 두고, 신규 생성 시 필수 여부는 CreateScenarioRequest에서 강제한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_node_id")
    private MapNode startNode;

    // ERD 기준 admin_id (팀원 원본 코드의 user_id에서 변경)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TrainingScenario create(String name,
                                          Integer expectedParticipants,
                                          Integer targetEvacuationSec,
                                          Instant scheduledAt,
                                          Boolean isTemplate,
                                          FireSpreadSpeed fireSpreadSpeed,
                                          Building building,
                                          User admin,
                                          MapNode startNode) {
        TrainingScenario scenario = new TrainingScenario();
        scenario.name = name;
        scenario.expectedParticipants = expectedParticipants;
        scenario.targetEvacuationSec = targetEvacuationSec;
        scenario.scheduledAt = scheduledAt;
        scenario.isTemplate = isTemplate != null ? isTemplate : false;
        scenario.fireSpreadSpeed = fireSpreadSpeed != null ? fireSpreadSpeed : FireSpreadSpeed.MEDIUM;
        scenario.building = building;
        scenario.admin = admin;
        scenario.startNode = startNode;
        // 생성 시점엔 필수 필드가 모두 채워져 있어 바로 실행 가능한 상태로 시작한다.
        // 초안 저장(DRAFT) 플로우는 별도 엔드포인트가 생기면 그때 지정한다.
        scenario.status = ScenarioStatus.READY;
        return scenario;
    }

    public void update(String name,
                       Integer expectedParticipants,
                       Integer targetEvacuationSec,
                       Instant scheduledAt,
                       Boolean isTemplate,
                       FireSpreadSpeed fireSpreadSpeed,
                       MapNode startNode) {
        if (name != null) this.name = name;
        if (expectedParticipants != null) this.expectedParticipants = expectedParticipants;
        if (targetEvacuationSec != null) this.targetEvacuationSec = targetEvacuationSec;
        if (scheduledAt != null) this.scheduledAt = scheduledAt;
        if (isTemplate != null) this.isTemplate = isTemplate;
        if (fireSpreadSpeed != null) this.fireSpreadSpeed = fireSpreadSpeed;
        if (startNode != null) this.startNode = startNode;
    }

    // 상태 전이 가드는 이 엔티티가 아니라 TrainingSessionService가 담당한다
    // (RouteRecalculation, IoTLight와 동일한 컨벤션 - 엔티티는 단순 세터).
    public void markInProgress() {
        this.status = ScenarioStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = ScenarioStatus.COMPLETED;
    }

    public void markError() {
        this.status = ScenarioStatus.ERROR;
    }

    // 응답용 getter
    public UUID getBuildingId() {
        return building != null ? building.getId() : null;
    }

    public UUID getAdminId() {
        return admin != null ? admin.getId() : null;
    }

    public UUID getStartNodeId() {
        return startNode != null ? startNode.getId() : null;
    }
}