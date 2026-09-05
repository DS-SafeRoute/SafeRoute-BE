package com.saferoute.domain.analysis.service;

import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.domain.analysis.AiAnalysisClient;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.AnalysisErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.api.error.FloorErrorCode;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FloorAnalysisService {

  private final FloorRepository floorRepository;
  private final MapNodeJpaRepository mapNodeRepository;
  private final MapEdgeJpaRepository mapEdgeRepository;
  private final AiAnalysisClient aiAnalysisClient;

  public void analyzeFloor(UUID floorId) {
    Floor floor = floorRepository.findById(floorId)
        .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

    AnalyseFloorResponse response;
    try {
      response = aiAnalysisClient.analyze(
          floor.getMapImageKey(), floor.getRealWidth(), floor.getRealHeight()
      );
    } catch (Exception e) {
      markAsFailed(floorId);   // 짧은 트랜잭션
      throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_FAILED);
    }

    try {
      persistAnalysisResult(floorId, response);   // 짧은 트랜잭션
    } catch (Exception e) {
      markAsFailed(floorId);
      throw e;
    }
  }

  @Transactional
  public void markAsFailed(UUID floorId) {
    Floor floor = floorRepository.findById(floorId)
        .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
    floor.updateSegmentationStatus(SegmentationStatus.FAILED);
  }

  @Transactional
  public void persistAnalysisResult(UUID floorId, AnalyseFloorResponse response) {
    Floor floor = floorRepository.findById(floorId)
        .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

    validateGraphIntegrity(response);
    replaceExistingGraph(floor);

    Map<String, MapNode> tempIdToNode = new HashMap<>();
    for (var n : response.nodes()) {
      NodeType type = NodeType.valueOf(n.type());
      MapNode node = MapNode.create(floor, n.code(), type, defaultNameFor(type), n.x(), n.y(), false);
      mapNodeRepository.save(node);
      tempIdToNode.put(n.tempId(), node);
    }

    for (var e : response.edges()) {
      MapEdge edge = MapEdge.create(
          floor, tempIdToNode.get(e.fromTempId()), tempIdToNode.get(e.toTempId()),
          e.distance(), e.bidirectional()
      );
      mapEdgeRepository.save(edge);
    }

    floor.applyPlanSize(response.planWidthPx(), response.planHeightPx());
    floor.updateSegmentationStatus(SegmentationStatus.DONE);
  }

  private void validateGraphIntegrity(AnalyseFloorResponse response) {
    Set<String> tempIds = new HashSet<>();
    for (var n : response.nodes()) {
      if (!tempIds.add(n.tempId())) {
        throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_INVALID_GRAPH);
      }
    }
    for (var e : response.edges()) {
      if (!tempIds.contains(e.fromTempId()) || !tempIds.contains(e.toTempId())) {
        throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_INVALID_GRAPH);
      }
    }
  }

  private void replaceExistingGraph(Floor floor) {
    // 엣지 먼저 (FK가 노드를 참조하니까), 그 다음 노드
    mapEdgeRepository.deleteAllByFloor(floor);
    mapNodeRepository.deleteAllByFloor(floor);
  }

  private String defaultNameFor(NodeType type) {
    return switch (type) {
      case ROOM -> "방";
      case HALLWAY -> "복도";
      case STAIR -> "계단";
      case DOOR -> "문";
      default -> "노드";
    };
  }
}
