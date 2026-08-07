package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.service.CongestionEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혼잡도", description = "CCTV 혼잡 이벤트 수신 API")
@RestController
@RequestMapping("/api/v1/congestion-events")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionEventService congestionEventService;

    @PostMapping
    public void reportCongestion(@Valid @RequestBody ReportCongestionRequest request) {
        congestionEventService.reportCongestion(request);
    }
}
