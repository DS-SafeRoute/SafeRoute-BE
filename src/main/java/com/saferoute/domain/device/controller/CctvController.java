package com.saferoute.domain.device.controller;

import com.saferoute.domain.device.dto.request.ConfigureCctvGridCellsRequest;
import com.saferoute.domain.device.dto.request.CreateCctvRequest;
import com.saferoute.domain.device.dto.response.CctvResponse;
import com.saferoute.domain.device.service.CctvService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.CctvSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CCTV", description = "CCTV 등록/조회 및 감시 GridCell 설정 API")
@RestController
@RequestMapping("/api/v1/cctvs")
@RequiredArgsConstructor
public class CctvController {

    private final CctvService cctvService;

    @PostMapping
    public ResponseEntity<ApiResponse<CctvResponse>> createCctv(
            @Valid @RequestBody CreateCctvRequest request
    ) {
        CctvResponse response = cctvService.createCctv(request);
        return ResponseEntity.status(CctvSuccessCode.CCTV_CREATED.getHttpStatus())
                .body(ApiResponse.success(CctvSuccessCode.CCTV_CREATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CctvResponse>>> getCctvs(
            @RequestParam(required = false) UUID floorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_LIST_FOUND,
                cctvService.getCctvs(floorId)
        ));
    }

    @GetMapping("/{cctvId}")
    public ResponseEntity<ApiResponse<CctvResponse>> getCctv(@PathVariable UUID cctvId) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_DETAIL_FOUND,
                cctvService.getCctv(cctvId)
        ));
    }

    @GetMapping("/{cctvId}/grid-cells")
    public ResponseEntity<ApiResponse<CctvResponse>> getGridCells(@PathVariable UUID cctvId) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_GRID_CELLS_FOUND,
                cctvService.getGridCells(cctvId)
        ));
    }

    @PutMapping("/{cctvId}/grid-cells")
    public ResponseEntity<ApiResponse<CctvResponse>> configureGridCells(
            @PathVariable UUID cctvId,
            @Valid @RequestBody ConfigureCctvGridCellsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_GRID_CELLS_CONFIGURED,
                cctvService.configureGridCells(cctvId, request)
        ));
    }

    @PatchMapping("/{cctvId}/enable")
    public ResponseEntity<ApiResponse<CctvResponse>> enableCctv(@PathVariable UUID cctvId) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_ENABLED,
                cctvService.enableCctv(cctvId)
        ));
    }

    @PatchMapping("/{cctvId}/disable")
    public ResponseEntity<ApiResponse<CctvResponse>> disableCctv(@PathVariable UUID cctvId) {
        return ResponseEntity.ok(ApiResponse.success(
                CctvSuccessCode.CCTV_DISABLED,
                cctvService.disableCctv(cctvId)
        ));
    }
}
