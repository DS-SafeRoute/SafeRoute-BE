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
import java.util.Map;
import java.util.HashMap;
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

  @Transactional
  public void analyzeFloor(UUID floorId) {
    Floor floor = floorRepository.findById(floorId)
        .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

    try {
      AnalyseFloorResponse response = aiAnalysisClient.analyze(
          floor.getMapImageKey(), floor.getRealWidth(), floor.getRealHeight()
      );

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

      floor.applyGridConfig(null, null, null, response.planWidthPx(), response.planHeightPx());
      floor.updateSegmentationStatus(SegmentationStatus.DONE);

    } catch (Exception e) {
      floor.updateSegmentationStatus(SegmentationStatus.FAILED);
      throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_FAILED);
    }
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