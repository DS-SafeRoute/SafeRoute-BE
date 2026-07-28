package com.saferoute.domain.floor.service;

import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.training.repository.FireZoneRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Floor를 삭제하기 전(또는 Building cascade로 Floor가 삭제되기 전) floor_id를 참조하는
// 하위 엔티티들을 FK 위반이 나지 않는 순서로 먼저 정리한다.
// 순서 근거: FireZone/MapEdgeGridCell/Cctv/IoTLight는 다른 어떤 엔티티도 그것들을 참조하지 않으므로
// 먼저 지워도 안전하다. MapEdge는 Cctv/IoTLight/MapEdgeGridCell이 먼저 지워진 뒤에야 안전하게 지울 수 있고,
// MapNode는 MapEdge/IoTLight가 지워진 뒤, FloorGridCell은 FireZone/MapEdgeGridCell이 지워진 뒤에야 안전하다.
@Service
@RequiredArgsConstructor
public class FloorCleanupService {

    private final FireZoneRepository fireZoneRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;
    private final CctvJpaRepository cctvJpaRepository;
    private final IoTLightJpaRepository ioTLightJpaRepository;
    private final MapEdgeJpaRepository mapEdgeJpaRepository;
    private final MapNodeJpaRepository mapNodeJpaRepository;
    private final FloorGridCellRepository floorGridCellRepository;

    @Transactional
    public void cleanupFloorChildren(UUID floorId) {
        fireZoneRepository.deleteAllByFloor_Id(floorId);
        mapEdgeGridCellRepository.deleteAllByMapEdge_Floor_Id(floorId);
        cctvJpaRepository.deleteAllByFloor_Id(floorId);
        ioTLightJpaRepository.deleteAllByFloor_Id(floorId);
        mapEdgeJpaRepository.deleteAllByFloor_Id(floorId);
        mapNodeJpaRepository.deleteAllByFloor_Id(floorId);
        floorGridCellRepository.deleteAllByFloor_Id(floorId);
    }
}