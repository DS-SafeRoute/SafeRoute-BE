package com.saferoute.domain.analysis;

import com.saferoute.domain.analysis.dto.AnalyseFloorRequest;
import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.global.api.error.AnalysisErrorCode;
import com.saferoute.global.api.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiAnalysisClient {

  private final RestClient restClient;

  public AiAnalysisClient(@Value("${saferoute.ai-service.base-url}") String baseUrl) {
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  public AnalyseFloorResponse analyze(String imageKey, double realWidthM, double realHeightM) {
    var request = new AnalyseFloorRequest(imageKey, realWidthM, realHeightM);

    return restClient.post()
        .uri("/analyze-floorplan")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
          throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_REQUEST_INVALID);
        })
        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
          throw new ApiException(AnalysisErrorCode.AI_ANALYSIS_FAILED);
        })
        .body(AnalyseFloorResponse.class);
  }
}