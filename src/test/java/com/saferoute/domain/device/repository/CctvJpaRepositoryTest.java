package com.saferoute.domain.device.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.config.JpaAuditingConfig;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class CctvJpaRepositoryTest {

    @Autowired
    private CctvJpaRepository cctvJpaRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private MapNodeJpaRepository mapNodeJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("CCTV 코드 조회 시 혼잡 설정에 필요한 위치 연관관계를 함께 조회한다")
    void findByCode_fetchesLocationGraph() {
        Building building = buildingRepository.save(Building.create(
                "테스트관",
                "서울특별시 안전구 테스트로 123",
                BuildingType.CLASSROOM,
                "SafeRoute School"
        ));
        Floor floor = floorRepository.save(Floor.create(building, 1));
        MapNode customNode = mapNodeJpaRepository.save(MapNode.createCustom(
                floor,
                "CCTV_001",
                "복도 CCTV",
                0.5,
                0.5,
                CustomDeviceType.CCTV
        ));
        cctvJpaRepository.save(Cctv.create("CCTV_001", "복도 CCTV", customNode));
        entityManager.flush();
        entityManager.clear();

        Cctv found = cctvJpaRepository.findByCode("CCTV_001").orElseThrow();

        assertThat(Hibernate.isInitialized(found.getCustomNode())).isTrue();
        assertThat(Hibernate.isInitialized(found.getCustomNode().getFloor())).isTrue();
        assertThat(Hibernate.isInitialized(found.getCustomNode().getFloor().getBuilding())).isTrue();

        entityManager.clear();
        assertThatCode(() -> found.getCustomNode().getFloor().getBuilding().getId())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("건물의 활성 CCTV만 층과 코드 순으로 위치 정보와 함께 조회한다")
    void findMonitoringCctvs_filtersAndOrdersWithLocationGraph() {
        Building targetBuilding = buildingRepository.save(Building.create(
                "A동",
                "서울특별시 안전구 모니터링로 123",
                BuildingType.CLASSROOM,
                "SafeRoute School"
        ));
        Building otherBuilding = buildingRepository.save(Building.create(
                "B동",
                "서울특별시 안전구 모니터링로 456",
                BuildingType.CLASSROOM,
                "SafeRoute School"
        ));
        Floor firstFloor = floorRepository.save(Floor.create(targetBuilding, 1));
        Floor secondFloor = floorRepository.save(Floor.create(targetBuilding, 2));
        Floor otherFloor = floorRepository.save(Floor.create(otherBuilding, 1));

        saveCctv(secondFloor, "CCTV_001");
        saveCctv(firstFloor, "CCTV_003");
        saveCctv(firstFloor, "CCTV_002");
        Cctv disabled = saveCctv(firstFloor, "CCTV_004");
        disabled.disable();
        saveCctv(otherFloor, "CCTV_005");
        entityManager.flush();
        entityManager.clear();

        List<Cctv> found = cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        targetBuilding.getId());

        assertThat(found)
                .extracting(Cctv::getCode)
                .containsExactly("CCTV_002", "CCTV_003", "CCTV_001");
        assertThat(found).allSatisfy(cctv -> {
            assertThat(Hibernate.isInitialized(cctv.getCustomNode())).isTrue();
            assertThat(Hibernate.isInitialized(cctv.getCustomNode().getFloor())).isTrue();
            assertThat(Hibernate.isInitialized(cctv.getCustomNode().getFloor().getBuilding())).isTrue();
        });
    }

    private Cctv saveCctv(Floor floor, String code) {
        MapNode customNode = mapNodeJpaRepository.save(MapNode.createCustom(
                floor,
                code,
                code + " 카메라",
                0.5,
                0.5,
                CustomDeviceType.CCTV
        ));
        return cctvJpaRepository.save(Cctv.create(code, code + " 카메라", customNode));
    }
}
