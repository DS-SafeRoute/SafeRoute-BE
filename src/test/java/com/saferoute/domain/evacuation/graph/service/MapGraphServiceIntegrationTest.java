package com.saferoute.domain.evacuation.graph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.evacuation.graph.dto.request.CreateMapEdgeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.UpdateMapNodePositionRequest;
import com.saferoute.domain.evacuation.graph.dto.response.MapNodeResponse;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 그리드가 생성된 층에서, 이미 엣지가 연결된 노드의 타입/EXIT 대상 여부를 PATCH로 바꿀 때
// FloorGridService.recomputeEdgeGridCells가 LazyInitializationException 없이 끝나는지 검증한다.
// Mockito 목으로는 이 버그(영속성 컨텍스트 clear 이후 지연 로딩 프록시 초기화 실패)를 재현할 수
// 없어 실제 JPA/DB(H2)를 쓰는 통합 테스트로 둔다.
@SpringBootTest
class MapGraphServiceIntegrationTest {

    @Autowired
    private MapGraphService mapGraphService;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private MapNodeJpaRepository mapNodeJpaRepository;

    @Autowired
    private FloorGridService floorGridService;

    @Test
    @Transactional
    @DisplayName("그리드/연결 엣지가 있는 STAIR 노드를 EXIT로 바꾸고 isExitTarget을 켜면 500 없이 성공한다")
    void updateNodePosition_stairToExitWithGridAndEdge_succeeds() {
        Building building = buildingRepository.save(
                Building.create("공학관", "서울특별시 성북구 안전로 1", BuildingType.CLASSROOM, "SafeRoute School"));
        Floor floor = floorRepository.save(Floor.create(building, 1));
        floor.updateSegmentationStatus(SegmentationStatus.DONE);
        floor.upload(10.0, 10.0, "floors/test-map.png");
        floorRepository.save(floor);
        floorGridService.createOrRegenerateGrid(floor.getId(), new CreateOrUpdateFloorGridRequest(0.5));

        MapNode stair = mapNodeJpaRepository.save(
                MapNode.create(floor, "STAIR_A", NodeType.STAIR, "계단A", 0.3, 0.3, false));
        MapNode hallway = mapNodeJpaRepository.save(
                MapNode.create(floor, "HALLWAY_A", NodeType.HALLWAY, "복도A", 0.5, 0.5, false));
        // 엣지 생성 직후의 그리드 셀 재계산(MapEdgeGridCellRepository.deleteAllByMapEdgeId의
        // clearAutomatically)이 영속성 컨텍스트를 비워, 아래 PATCH에서 다시 조회되는 MapEdge의
        // fromNode/toNode가 지연 로딩 프록시가 되는 상황을 재현하는 데 필요하다.
        mapGraphService.createEdge(new CreateMapEdgeRequest(stair.getId(), hallway.getId(), 5.0, true));

        UpdateMapNodePositionRequest request =
                new UpdateMapNodePositionRequest(0.3, 0.3, true, NodeType.EXIT);

        MapNodeResponse[] response = new MapNodeResponse[1];
        assertThatCode(() -> response[0] = mapGraphService.updateNodePosition(stair.getId(), request))
                .doesNotThrowAnyException();

        assertThat(response[0].type()).isEqualTo(NodeType.EXIT);
        assertThat(response[0].isExitTarget()).isTrue();
    }
}
