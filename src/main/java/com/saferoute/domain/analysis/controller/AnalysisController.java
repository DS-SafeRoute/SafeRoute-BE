package com.saferoute.domain.analysis.controller;

import com.saferoute.domain.floor.service.FloorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 분석", description = "도면 AI 세그멘테이션 분석 요청 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

  private final FloorService floorService;

    @PostMapping("/{floorId}/analyse")
    public void analyze(@PathVariable UUID floorId) {
      floorService.requestAnalysis(floorId);
    }
}
