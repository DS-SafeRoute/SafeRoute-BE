package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapEdgeGridCellRepository extends JpaRepository<MapEdgeGridCell, UUID> {

    // 화재/혼잡이 발생한 셀이 어떤 Edge에 영향을 주는지
    List<MapEdgeGridCell> findAllByGridCell_Id(UUID gridCellId);

    List<MapEdgeGridCell> findAllByGridCell_IdIn(List<UUID> gridCellIds);

    List<MapEdgeGridCell> findAllByMapEdge_Id(UUID mapEdgeId);

    // 엣지 하나의 grid cell 매핑을 다시 계산하기 전, 기존 매핑을 지운다
    // (엣지 추가/노드 이동으로 인한 재계산용 - FloorGridService.recomputeEdgeGridCells 참고).
    // clearAutomatically는 쓰지 않는다 - 호출 직후 recomputeEdgeGridCells가 같은 트랜잭션에서
    // edge.getFromNode()/getToNode()에 접근하는데, clear()가 영속성 컨텍스트를 비우면 그 lazy
    // proxy들이 detach되어 LazyInitializationException("no session")이 발생한다.
    @Modifying(flushAutomatically = true)
    @Query("delete from MapEdgeGridCell m where m.mapEdge.id = :mapEdgeId")
    int deleteAllByMapEdgeId(@Param("mapEdgeId") UUID mapEdgeId);
}