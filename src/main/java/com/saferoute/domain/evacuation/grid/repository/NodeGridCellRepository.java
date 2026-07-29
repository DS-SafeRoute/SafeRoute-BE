package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.NodeGridCell;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeGridCellRepository extends JpaRepository<NodeGridCell, UUID> {

    // 특정 셀 안에 어떤 노드들이 있는지 (도면 클릭 시 "여기 뭐 있나" 표시용)
    List<NodeGridCell> findAllByGridCell_Id(UUID gridCellId);

    // 노드 하나는 셀 하나에만 소속되므로 단건 조회
    Optional<NodeGridCell> findByNode_Id(UUID nodeId);

    // 혼잡도 처리 시 CCTV customNode -> 셀 조회용
    List<NodeGridCell> findAllByNode_IdIn(List<UUID> nodeIds);
}