package com.saferoute.domain.dashboard;

import com.saferoute.domain.training.dto.DashboardStatsResponse;
import com.saferoute.domain.report.dto.RecentTrainingReportResponse;
import com.saferoute.domain.training.dto.TrainingStatusResponse;
import com.saferoute.domain.report.service.TrainingReportService;
import com.saferoute.domain.training.service.TrainingSessionService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드", description = "훈련 현황 및 통계 대시보드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final TrainingReportService trainingReportService;
  private final TrainingSessionService trainingSessionService;

  @Operation(
      summary = "최근 훈련 리포트 목록 조회",
      description = """
          요청자가 속한 학교에서 생성된 훈련 리포트 중 최신 5건을 생성일(createdAt) 기준
          내림차순으로 반환합니다. 대시보드 메인 화면의 "최근 훈련" 목록에 사용됩니다.

          각 항목은 리포트 상세 조회용 reportId(shortId)와 함께 시나리오 이름, 세션 시작 시각,
          참여 인원, 평균(세션 전체) 대피 소요 시간, 생존률, 등급으로 구성되며, 리포트가 아직
          생성되지 않은 세션은 이 목록에 포함되지 않습니다.

          현재 5건으로 개수가 고정되어 있으며 페이지네이션은 지원하지 않습니다.
          """
  )
  @GetMapping("/trainings")
  public ResponseEntity<ApiResponse<List<RecentTrainingReportResponse>>> getRecentReports(
      Authentication authentication
  ) {
    List<RecentTrainingReportResponse> response =
        trainingReportService.getRecentTrainingReport(authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(
      summary = "대시보드 통계 조회",
      description = """
          요청자가 속한 학교 전체를 대상으로 한 요약 통계를 반환합니다.

          totalSessions는 학교에 속한 전체 훈련 세션 수(리포트 생성 여부와 무관)이지만,
          avgEvacuationSec·avgSurvivalRate·totalParticipants 세 값은 리포트가 생성된
          세션만 집계 대상이므로, 리포트가 없는 세션이 있으면 totalSessions보다 실제 집계에
          사용된 세션 수가 더 적을 수 있습니다. avgEvacuationSec/avgSurvivalRate는 리포트
          기준 평균, totalParticipants는 리포트에 기록된 참여 인원의 합계입니다.

          해당 학교에 생성된 리포트가 하나도 없으면 avgEvacuationSec, avgSurvivalRate,
          totalParticipants는 모두 0으로 반환됩니다.
          """
  )
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(
      Authentication authentication
  ) {
    DashboardStatsResponse response = trainingReportService.getStats(authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(
      summary = "훈련 세션 실시간 상태 조회",
      description = """
          훈련 세션의 현재 상태(SCHEDULED 또는 RUNNING)에 따라 서로 다른 응답 형태를
          반환합니다. 응답에 상태를 구분하는 별도 필드는 없으므로, 프론트엔드는 응답에 포함된
          필드 조합(scheduledAt/totalFloors/expectedParticipants가 있으면 예정된 훈련,
          elapsedSeconds/actualParticipants/currentSurvivalRate가 있으면 진행 중인 훈련)으로
          두 형태를 구분해야 합니다.

          SCHEDULED 상태에서는 건물명, 총 층수, 예정 시각, 예상 참여 인원을 반환합니다.

          RUNNING 상태에서는 건물명과 함께, 세션 시작 시각부터 현재까지 경과한 시간(초)을
          매 요청마다 서버에서 새로 계산해 반환합니다. actualParticipants와
          currentSurvivalRate는 아직 값이 채워지지 않았으면 각각 0으로 대체되어 내려갑니다.

          SCHEDULED, RUNNING 외의 상태(예: 종료된 세션)는 아직 지원하지 않으며 오류가
          발생하므로, 실시간 모니터링 용도로만 폴링해 사용해야 합니다.
          """
  )
  @GetMapping("/training-status/{sessionId}")
  public ResponseEntity<ApiResponse<TrainingStatusResponse>> getTrainingStatus(
      @PathVariable UUID sessionId,
      Authentication authentication) {
    TrainingStatusResponse response =
        trainingSessionService.getTrainingStatus(sessionId, authentication.getName());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

}
