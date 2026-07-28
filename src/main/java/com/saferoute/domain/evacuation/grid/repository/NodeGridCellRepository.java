package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.NodeGridCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeGridCellRepository extends JpaRepository<NodeGridCell, UUID> {

    // 특정 셀 안에 어떤 노드들이 있는지 (도면 클릭 시 "여기 뭐 있나" 표시용)
    List<NodeGridCell> findAllByGridCell_Id(UUID gridCellId);

    // 특정 노드가 어느 셀(들)에 걸쳐있는지
    List<NodeGridCell> findAllByNode_Id(UUID nodeId);
}