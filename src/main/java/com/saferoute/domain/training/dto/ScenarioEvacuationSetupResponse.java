package com.saferoute.domain.training.dto;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingScenario;
import java.time.Instant;
import java.util.UUID;

// 시나리오 설정 화면이 발화점(gridCell)과 훈련 시작점(START 노드)을 한 번에 조회/표시할 때 쓰는 응답.
// 설정 전에는 fireOrigin, startNode, configuredAt이 모두 null이다(설정 여부를 404가 아니라
// 이 필드들로 판단하게 하기 위함).
public record ScenarioEvacuationSetupResponse(
        UUID scenarioId,
        UUID buildingId,
        UUID floorId,
        FireOrigin fireOrigin,
        StartNode startNode,
        Instant configuredAt
) {

    public record FireOrigin(
            UUID fireZoneId,
            UUID gridCellId,
            int rowIndex,
            int columnIndex,
            double centerX,
            double centerY
    ) {
        public static FireOrigin from(FireZone fireZone, FloorGridCell cell) {
            return new FireOrigin(
                    fireZone.getId(),
                    cell.getId(),
                    cell.getRowIndex(),
                    cell.getColumnIndex(),
                    cell.getCenterX(),
                    cell.getCenterY());
        }
    }

    public record StartNode(
            UUID nodeId,
            String code,
            String name,
            NodeType type,
            double x,
            double y
    ) {
        public static StartNode from(MapNode node) {
            return new StartNode(node.getId(), node.getCode(), node.getName(), node.getType(), node.getX(), node.getY());
        }
    }

    public static ScenarioEvacuationSetupResponse notConfigured(TrainingScenario scenario) {
        return new ScenarioEvacuationSetupResponse(
                scenario.getId(), scenario.getBuildingId(), null, null, null, null);
    }

    public static ScenarioEvacuationSetupResponse of(
            TrainingScenario scenario, FireZone fireZone, FloorGridCell cell, MapNode startNode, Instant configuredAt) {
        return new ScenarioEvacuationSetupResponse(
                scenario.getId(),
                scenario.getBuildingId(),
                cell.getFloor().getId(),
                FireOrigin.from(fireZone, cell),
                StartNode.from(startNode),
                configuredAt);
    }
}
