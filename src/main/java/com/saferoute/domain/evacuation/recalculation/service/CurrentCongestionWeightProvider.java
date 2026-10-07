package com.saferoute.domain.evacuation.recalculation.service;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.telemetry.dynamo.entity.CurrentCctvStateItem;
import com.saferoute.domain.telemetry.dynamo.repository.CurrentCctvStateRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 한 세션의 한 층에서 "지금 이 순간" 혼잡한 엣지들의 가중치 배율 맵을 만든다.
// 혼잡 트리거(RouteRecalculationService.trigger)는 그 트리거를 낸 CCTV 한 대의 엣지만 알면 되지만,
// 화재 우회 후보는 혼잡 이벤트와 무관한 시점에 계산되므로 층 전체의 현재 혼잡 상태가 필요하다.
// 기준 데이터는 CCTV별 최신 관측값(CurrentCctvStateItem)이다.
// CCTV 현재 상태 -> CCTV 감시 GridCell -> MapEdgeGridCell -> MapEdge
// 조회 실패(DynamoDB 장애 등)를 호출부(RouteRecalculationService)가 잡아서 혼잡 없이 계속 진행하므로,
// 예외가 이 프록시를 통과하면서 호출부 트랜잭션을 rollback-only로 오염시키지 않게 한다 - 읽기 전용이라
// 되돌릴 변경도 없다(그러지 않으면 호출부가 예외를 삼켜도 커밋 시점에 UnexpectedRollbackException).
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
public class CurrentCongestionWeightProvider {

    // 화재 우회 후보에서는 VERY_CROWDED도 완전 제외가 아니라 큰 페널티로만 반영한다.
    // 혼잡 우회(trigger)와 달리 화재 우회는 "혼잡한 길밖에 안 남은" 상황에서도 반드시 후보를
    // 내야 하므로, 혼잡 때문에 EVACUATION_ROUTE_NOT_FOUND가 나는 일이 없어야 한다.
    static final double VERY_CROWDED_WEIGHT_MULTIPLIER = 10.0;

    private final CurrentCctvStateRepository currentCctvStateRepository;
    private final CctvJpaRepository cctvJpaRepository;
    private final CctvGridCellRepository cctvGridCellRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;

    // 혼잡하지 않은 엣지(배율 1.0)는 담지 않는다. 여러 CCTV가 같은 엣지를 감시하면 가장 큰 배율을 쓴다.
    public Map<UUID, Double> currentMultipliers(UUID sessionId, UUID floorId) {
        Map<String, Double> multiplierByCctvCode = new HashMap<>();
        for (CurrentCctvStateItem state : currentCctvStateRepository.findAllBySessionId(sessionId.toString())) {
            double multiplier = multiplierFor(state.getCongestionLevel());
            if (multiplier > 1.0) {
                multiplierByCctvCode.put(state.getCctvCode(), multiplier);
            }
        }
        if (multiplierByCctvCode.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Double> multiplierByCctvId = new HashMap<>();
        for (Cctv cctv : cctvJpaRepository.findAllByCustomNode_Floor_Id(floorId)) {
            Double multiplier = multiplierByCctvCode.get(cctv.getCode());
            if (multiplier != null) {
                multiplierByCctvId.put(cctv.getId(), multiplier);
            }
        }
        if (multiplierByCctvId.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Double> multiplierByCellId = new HashMap<>();
        for (CctvGridCell mapping : cctvGridCellRepository.findAllByCctvIdsWithGridCell(
                new ArrayList<>(multiplierByCctvId.keySet()))) {
            multiplierByCellId.merge(
                    mapping.getGridCell().getId(), multiplierByCctvId.get(mapping.getCctv().getId()), Math::max);
        }
        if (multiplierByCellId.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Double> multiplierByEdgeId = new HashMap<>();
        for (MapEdgeGridCell edgeCell : mapEdgeGridCellRepository.findAllByGridCell_IdIn(
                List.copyOf(multiplierByCellId.keySet()))) {
            multiplierByEdgeId.merge(
                    edgeCell.getMapEdge().getId(), multiplierByCellId.get(edgeCell.getGridCell().getId()), Math::max);
        }
        return multiplierByEdgeId;
    }

    private double multiplierFor(CongestionLevel level) {
        if (level == null) {
            return 1.0;
        }
        return switch (level) {
            case NORMAL -> 1.0;
            case CAUTION -> RouteRecalculationService.CAUTION_WEIGHT_MULTIPLIER;
            case CROWDED -> RouteRecalculationService.CROWDED_WEIGHT_MULTIPLIER;
            case VERY_CROWDED -> VERY_CROWDED_WEIGHT_MULTIPLIER;
        };
    }
}
