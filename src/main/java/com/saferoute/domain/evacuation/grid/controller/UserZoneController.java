package com.saferoute.domain.evacuation.grid.controller;

import com.saferoute.domain.evacuation.grid.dto.request.UserZoneCreateRequest;
import com.saferoute.domain.evacuation.grid.dto.response.AllUserZoneResponse;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneCellsResponse;
import com.saferoute.domain.evacuation.grid.dto.response.UserZoneResponse;
import com.saferoute.domain.evacuation.grid.service.UserZoneService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Tag(name = "유저 구역", description = "유저구역 등록/조회/수정/삭제 API")
@RestController
@RequestMapping("/api/v1/floors/{floorId}/user-zones")
@RequiredArgsConstructor
@Validated
public class UserZoneController {

    private final UserZoneService userZoneService;

    @Operation(
            summary = "사용자 구역 생성",
            description = """
                    지정한 층의 그리드 셀 여러 개를 name으로 묶어 사람이 읽을 수 있는 구역
                    (예: "301호 앞 복도", "3층 왼쪽 계단")으로 등록합니다. cellIds로 넘긴
                    FloorGridCell들이 이 구역에 일괄 편입됩니다.

                    name은 같은 층 안에서 유일해야 하며 이미 존재하면 오류가 발생합니다.
                    cellIds에 존재하지 않는 셀 id가 섞여 있거나, 다른 층의 셀 id가 섞여 있으면
                    요청 전체가 거부됩니다(부분 성공 없음). 이미 다른 구역에 속한 셀을 다시
                    지정하면 소속 구역이 이 구역으로 덮어써집니다.
                    """
    )
    @PostMapping
    public ApiResponse<UserZoneResponse> createUserZone(
            @PathVariable UUID floorId,
            @RequestBody UserZoneCreateRequest request
    ) {
        return ApiResponse.success(userZoneService.create(floorId, request));
    }
    
    @Operation(
            summary = "사용자 구역 삭제",
            description = """
                    사용자 구역을 삭제합니다. 삭제해도 구역에 편입되어 있던 그리드 셀 자체는
                    지워지지 않고 구역 미지정(userZone=null) 상태로 남습니다(SET NULL).

                    userZoneId가 존재하지 않거나, 존재하더라도 요청한 floorId가 속한 층의
                    구역이 아니면 찾을 수 없음 오류가 발생합니다.
                    """
    )
    @DeleteMapping("/{userZoneId}")
    public ResponseEntity<Void> deleteUserZone(
            @PathVariable UUID floorId,
            @PathVariable UUID userZoneId
    ){
        userZoneService.delete(floorId, userZoneId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "층 사용자 구역 목록 조회",
            description = """
                    지정한 층에 등록된 모든 사용자 구역의 id/이름 목록을 반환합니다. 개별 구역에
                    속한 셀 목록은 포함하지 않으며, 필요하면 구역 단건 조회 API를 따로 호출해야
                    합니다.

                    아직 구역이 하나도 등록되지 않은 층이면 userzones가 빈 배열로 반환됩니다.
                    """
    )
    @GetMapping
    public ApiResponse<AllUserZoneResponse> findAllUserZone(
            @PathVariable UUID floorId
    ){
        return ApiResponse.success(userZoneService.findAll(floorId));
    }

    @Operation(
            summary = "사용자 구역 단건 조회",
            description = """
                    사용자 구역 하나의 정보와, 그 구역에 편입된 그리드 셀 전체 목록을 함께
                    반환합니다. 도면 위에 구역 범위를 하이라이트해서 보여줄 때 사용합니다.

                    userZoneId가 존재하지 않거나, 존재하더라도 요청한 floorId가 속한 층의
                    구역이 아니면 찾을 수 없음 오류가 발생합니다. 구역에 편입된 셀이 없으면
                    cells가 빈 배열로 반환됩니다.
                    """
    )
    @GetMapping("/{userZoneId}")
    public ApiResponse<UserZoneCellsResponse> findUserZone(
            @PathVariable UUID floorId,
            @PathVariable UUID userZoneId
    ){
        return ApiResponse.success(userZoneService.findUserZone(floorId, userZoneId));
    }
}
