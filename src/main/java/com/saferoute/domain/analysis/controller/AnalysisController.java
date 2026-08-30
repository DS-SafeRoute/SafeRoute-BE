package com.saferoute.domain.analysis.controller;

import com.saferoute.domain.floor.service.FloorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@Tag(name = "AI 분석", description = "도면 AI 세그멘테이션 분석 요청 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

  private final FloorService floorService;

    @Operation(
            summary = "도면 AI 세그멘테이션 분석 요청",
            description = """
                    업로드된 층 도면 이미지를 AI 세그멘테이션 서버로 보내 분석하고, 그 결과로
                    받은 노드/엣지 그래프를 해당 층의 대피 경로 그래프로 저장합니다. 요청 스레드
                    안에서 AI 서버 호출까지 동기로 처리되므로, 응답이 올 때까지 시간이 걸릴 수
                    있습니다(폴링 없이 응답 자체가 완료 신호입니다).

                    분석에 성공하면 해당 층의 기존 노드/엣지를 모두 삭제하고 AI 응답으로 새로
                    교체하며, segmentationStatus가 DONE으로 바뀝니다. AI 호출이 실패하거나 결과
                    그래프가 유효하지 않으면(중복된 노드 임시 ID, 존재하지 않는 노드를 참조하는
                    엣지 등) 저장 없이 segmentationStatus가 FAILED로 바뀌고 오류가 반환됩니다.

                    도면 이미지가 아직 업로드되지 않은 층이면 요청할 수 없습니다. 이미
                    segmentationStatus가 PROCESSING인 층(분석이 진행 중)에는 중복 요청할 수
                    없으며, 이 경우 오류가 반환됩니다. 이 API는 응답 본문 없이 처리 결과를
                    HTTP 상태로만 알려줍니다.
                    """
    )
    @PostMapping("/{floorId}/analyse")
    public void analyze(@PathVariable UUID floorId, Authentication authentication) {
      floorService.requestAnalysis(floorId, authentication.getName());
    }
}
