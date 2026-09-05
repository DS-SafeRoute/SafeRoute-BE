package com.saferoute.domain.evacuation.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.graph.dto.request.UpdateMapNodePositionRequest;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 노드 이동 시 연결된 엣지의 grid cell 매핑을 재계산하는 경로(#220)가, 재계산 전 기존 매핑을
// 지우는 deleteAllByMapEdgeId의 clearAutomatically로 인해 edge.getFromNode()/getToNode() 접근 시
// LazyInitializationException("no session")을 던지던 회귀(#226 조사 중 발견)를 검증한다.
// 실제 Hibernate 세션/영속성 컨텍스트가 있어야 재현되므로 단위 테스트(Mockito)로는 못 잡고
// 통합 테스트가 필요하다.
@SpringBootTest
class RecomputeEdgeGridCellsIntegrationTest {

    @Autowired MapGraphService mapGraphService;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FloorRepository floorRepository;
    @Autowired MapNodeJpaRepository mapNodeJpaRepository;
    @Autowired MapEdgeJpaRepository mapEdgeJpaRepository;
    @Autowired FloorGridCellRepository floorGridCellRepository;
    @Autowired MapEdgeGridCellRepository mapEdgeGridCellRepository;

    @AfterEach
    void cleanUp() {
        mapEdgeGridCellRepository.deleteAllInBatch();
        floorGridCellRepository.deleteAllInBatch();
        mapEdgeJpaRepository.deleteAllInBatch();
        mapNodeJpaRepository.deleteAllInBatch();
        floorRepository.deleteAllInBatch();
        buildingRepository.deleteAllInBatch();
    }

    @Test
    void updateNodePosition_recomputesConnectedEdgeGridCellsWithoutLazyInitializationException() {
        Building building = buildingRepository.saveAndFlush(Building.create(
                "재계산관", "서울특별시 안전구 재계산로 1", BuildingType.CLASSROOM, "SafeRoute School"));
        Floor floor = floorRepository.saveAndFlush(Floor.create(building, 1));
        floor.applyGridCellConfig(1.0, 2, 2);
        floorRepository.saveAndFlush(floor);

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                floorGridCellRepository.saveAndFlush(
                        FloorGridCell.create(floor, row, column, true, (column + 0.5) / 2.0, (row + 0.5) / 2.0));
            }
        }

        MapNode fromNode = mapNodeJpaRepository.saveAndFlush(
                MapNode.create(floor, "HALLWAY_A", NodeType.HALLWAY, "복도A", 0.1, 0.1, false));
        MapNode toNode = mapNodeJpaRepository.saveAndFlush(
                MapNode.create(floor, "HALLWAY_B", NodeType.HALLWAY, "복도B", 0.9, 0.9, false));
        MapEdge edge = mapEdgeJpaRepository.saveAndFlush(MapEdge.create(floor, fromNode, toNode, 5.0, true));

        assertThatCode(() -> mapGraphService.updateNodePosition(
                fromNode.getId(), new UpdateMapNodePositionRequest(0.15, 0.15, false)))
                .doesNotThrowAnyException();

        List<com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell> mappings =
                mapEdgeGridCellRepository.findAllByMapEdge_Id(edge.getId());
        assertThat(mappings).isNotEmpty();
    }
}
