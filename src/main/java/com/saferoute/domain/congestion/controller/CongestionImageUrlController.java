package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.response.CongestionImageUrlResponse;
import com.saferoute.domain.congestion.service.CongestionImageUrlService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.CongestionSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혼잡 이미지", description = "관리자 화면의 혼잡 이미지 조회 URL 발급 API")
@RestController
@RequiredArgsConstructor
public class CongestionImageUrlController {

    private final CongestionImageUrlService congestionImageUrlService;

    @GetMapping("/api/v1/congestion-events/{eventId}/image-url")
    public ResponseEntity<ApiResponse<CongestionImageUrlResponse>> getEventImageUrl(@PathVariable UUID eventId) {
        CongestionImageUrlResponse response = congestionImageUrlService.getEventImageUrl(eventId);
        return ResponseEntity.ok(ApiResponse.success(CongestionSuccessCode.CONGESTION_IMAGE_URL_ISSUED, response));
    }

    @GetMapping("/api/v1/congestion-observations/{eventId}/image-url")
    public ResponseEntity<ApiResponse<CongestionImageUrlResponse>> getObservationImageUrl(
            @PathVariable UUID eventId
    ) {
        CongestionImageUrlResponse response = congestionImageUrlService.getObservationImageUrl(eventId);
        return ResponseEntity.ok(ApiResponse.success(CongestionSuccessCode.CONGESTION_IMAGE_URL_ISSUED, response));
    }
}
