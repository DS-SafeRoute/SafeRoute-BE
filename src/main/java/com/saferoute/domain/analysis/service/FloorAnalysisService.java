package com.saferoute.domain.analysis.service;

import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.domain.analysis.AiAnalysisClient;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.AnalysisErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.api.error.FloorErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FloorAnalysisService {

  private final FloorRepository floorRepository;
  private final AiAnalysisClient aiAnalysisClient;
  private final FloorAnalysisResultService resultService;
  private final FloorAnalysisStatusService statusService;

  public void analyzeFloor(UUID floorId) {
    Floor floor = floorRepository.findById(floorId)
        .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

    AnalyseFloorResponse response;
    try {
      response = aiAnalysisClient.analyze(
          floor.getMapImageKey(), floor.getRealWidth(), floor.getRealHeight()
      );
    } catch (Exception e) {
      statusService.markAsFailed(floorId);
      throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_FAILED);
    }

    try {
      resultService.persistAnalysisResult(floorId, response);
    } catch (Exception e) {
      // 결과 저장 트랜잭션이 롤백된 뒤 별도 트랜잭션으로 실패 상태를 남긴다.
      statusService.markAsFailed(floorId);
      throw e;
    }
  }

  // 기존 내부/테스트 호출 경로를 유지하면서 실제 트랜잭션은 별도 Spring bean에서 시작한다.
  public void persistAnalysisResult(UUID floorId, AnalyseFloorResponse response) {
    resultService.persistAnalysisResult(floorId, response);
  }
}
