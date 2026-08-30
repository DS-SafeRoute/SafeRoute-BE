package com.saferoute.domain.device.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.entity.LightCommand;
import com.saferoute.domain.device.entity.LightCommandStatus;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.config.JpaAuditingConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class LightCommandJpaRepositoryTest {

    @Autowired
    private LightCommandJpaRepository lightCommandJpaRepository;

    @Autowired
    private IoTLightJpaRepository iotLightJpaRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private MapNodeJpaRepository mapNodeJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("유도등의 최신 PENDING 명령 하나만 조회한다 (중간에 밀린 명령은 무시)")
    void findFirstByLight_IdAndStatus_returnsLatestPendingOnly() {
        IoTLight light = saveLight("LIGHT_001");
        LightCommand older = lightCommandJpaRepository.save(
                LightCommand.createPending(light, IoTLightDirection.LEFT));
        entityManager.flush();
        LightCommand newer = lightCommandJpaRepository.save(
                LightCommand.createPending(light, IoTLightDirection.RIGHT));
        entityManager.flush();
        entityManager.clear();

        Optional<LightCommand> found = lightCommandJpaRepository
                .findFirstByLight_IdAndStatusOrderByCreatedAtDesc(light.getId(), LightCommandStatus.PENDING);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(newer.getId());
        assertThat(found.get().getId()).isNotEqualTo(older.getId());
    }

    @Test
    @DisplayName("SENT 상태로 기준 시각 이전에 전송된 명령만 타임아웃 대상으로 조회한다")
    void findAllByStatusAndSentAtBefore_returnsOnlyStaleSentCommands() {
        IoTLight light = saveLight("LIGHT_002");

        LightCommand stale = LightCommand.createPending(light, IoTLightDirection.LEFT);
        stale.markSent(Instant.now().minus(1, ChronoUnit.HOURS));
        lightCommandJpaRepository.save(stale);

        LightCommand fresh = LightCommand.createPending(light, IoTLightDirection.RIGHT);
        fresh.markSent(Instant.now());
        lightCommandJpaRepository.save(fresh);

        LightCommand pending = LightCommand.createPending(light, IoTLightDirection.OFF);
        lightCommandJpaRepository.save(pending);

        entityManager.flush();
        entityManager.clear();

        List<LightCommand> found = lightCommandJpaRepository.findAllByStatusAndSentAtBefore(
                LightCommandStatus.SENT, Instant.now().minus(10, ChronoUnit.MINUTES));

        assertThat(found).extracting(LightCommand::getId).containsExactly(stale.getId());
    }

    private IoTLight saveLight(String code) {
        Building building = buildingRepository.save(Building.create(
                "테스트관",
                "서울특별시 안전구 테스트로 123",
                BuildingType.CLASSROOM,
                "SafeRoute School"
        ));
        Floor floor = floorRepository.save(Floor.create(building, 1));
        MapNode customNode = mapNodeJpaRepository.save(MapNode.createCustom(
                floor,
                code,
                code + " 유도등",
                0.5,
                0.5,
                CustomDeviceType.GUIDE_LIGHT
        ));
        return iotLightJpaRepository.save(IoTLight.create(code, code + " 유도등", customNode));
    }
}
