package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.IoTLight;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IoTLightJpaRepository extends JpaRepository<IoTLight, UUID> {

    List<IoTLight> findAllByCustomNode_Floor_Id(UUID floorId);

    List<IoTLight> findAllByCustomNode_Floor_Building_SchoolName(String schoolName);

    // 훈련 리포트의 경로 이탈률을 세션(=건물) 단위로 집계할 때, 그 건물에 설치된 모든 유도등을 조회한다.
    List<IoTLight> findAllByCustomNode_Floor_Building_Id(UUID buildingId);

    List<IoTLight> findAllByCustomNode_Floor_IdAndCustomNode_Floor_Building_SchoolName(
            UUID floorId, String schoolName);

    Optional<IoTLight> findByIdAndCustomNode_Floor_Building_SchoolName(UUID id, String schoolName);

    Optional<IoTLight> findByCode(String code);

    Optional<IoTLight> findByCustomNode_Id(UUID customNodeId);

    // 특정 분기점에 설치된 유도등 (경로 재계산 시 방향 지시 대상 조회용)
    List<IoTLight> findAllByDecisionNode_Id(UUID mapNodeId);

    // 경로 설정이 아직 안 된 기기 (훈련 시작 전 점검용)
    List<IoTLight> findAllByCustomNode_Floor_IdAndDecisionNodeIsNull(UUID floorId);
}
