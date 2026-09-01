package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import org.springframework.data.domain.Page;

public record FloorGridCellPageResponse(
        List<FloorGridCellResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        Double realWidth,
        Double realHeight,
        Integer rows,
        Integer columns,
        Double cellSizeMeter
) {
    public static FloorGridCellPageResponse from(Page<FloorGridCellResponse> result, Floor floor) {
        return new FloorGridCellPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                floor.getRealWidth(),
                floor.getRealHeight(),
                floor.getGridRows(),
                floor.getGridColumns(),
                floor.getGridCellSizeMeter()
        );
    }
}
