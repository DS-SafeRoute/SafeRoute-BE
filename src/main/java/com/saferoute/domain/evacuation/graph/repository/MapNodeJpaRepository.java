package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapNodeJpaRepository extends JpaRepository<MapNode, UUID> {

    List<MapNode> findAllByFloor_Id(UUID floorId);

    List<MapNode> findAllByFloor_IdAndType(UUID floorId, NodeType type);

    Optional<MapNode> findByIdAndFloor_Building_SchoolName(UUID id, String schoolName);

    // MapNode.code 순번 생성용 (Service에서 NODE_001 형태로 채번)
    long countByFloor_Id(UUID floorId);

    // 마지막 EXIT 노드 삭제 방지 체크용
    long countByFloor_IdAndIsExitTargetTrue(UUID floorId);

    boolean existsByFloor_IdAndCode(UUID floorId, String code);

    void deleteAllByFloor_Id(UUID floorId);

    void deleteAllByFloor(Floor floor);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MapNode n where n.floor.id = :floorId and n.customDeviceType = :type")
    int deleteAllByFloorIdAndCustomDeviceType(@Param("floorId") UUID floorId, @Param("type") CustomDeviceType type);

}
