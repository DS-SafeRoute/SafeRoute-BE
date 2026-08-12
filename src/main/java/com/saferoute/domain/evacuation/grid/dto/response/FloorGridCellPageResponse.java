package com.saferoute.domain.evacuation.grid.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record FloorGridCellPageResponse(
        List<FloorGridCellResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static FloorGridCellPageResponse from(Page<FloorGridCellResponse> result) {
        return new FloorGridCellPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
