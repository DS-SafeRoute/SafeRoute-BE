package com.saferoute.domain.evacuation.grid.service;

import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridResponse;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellResponse;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellPageResponse;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.entity.NodeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.NodeGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.UserZoneRepository;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.error.GridErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.domain.user.service.SchoolContextService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FloorGridService {

    private static final long MAX_GRID_CELL_COUNT = 1_000_000L; // 테스트 후 값 수정

    private final FloorRepository floorRepository;
    private final FloorGridCellRepository floorGridCellRepository;
    private final UserZoneRepository userZoneRepository;
    private final NodeGridCellRepository nodeGridCellRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;
    private final MapNodeJpaRepository mapNodeRepository;
    private final MapEdgeJpaRepository mapEdgeRepository;
    private final SchoolContextService schoolContextService;

    public FloorGridCellPageResponse getGridCells(UUID floorId, int page, int size) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("rowIndex").ascending().and(Sort.by("columnIndex").ascending())
        );
        return FloorGridCellPageResponse.from(
                floorGridCellRepository.findAllByFloor_Id(floorId, pageable)
                        .map(FloorGridCellResponse::from),
                floor
        );
    }

    public FloorGridCellPageResponse getGridCells(
            UUID floorId, int page, int size, String email) {
        validateFloorForSchool(floorId, email);
        return getGridCells(floorId, page, size);
    }

    @Transactional
    public FloorGridResponse createOrRegenerateGrid(
            UUID floorId, CreateOrUpdateFloorGridRequest request, String email) {
        validateFloorForSchool(floorId, email);
        return createOrRegenerateGrid(floorId, request);
    }

    private void validateFloorForSchool(UUID floorId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        if (floorRepository.findByIdAndBuilding_SchoolName(floorId, schoolName).isEmpty()) {
            throw new ApiException(FloorErrorCode.FLOOR_NOT_FOUND);
        }
    }

    // 그리드 생성/재생성 - 최초 생성이든 N번째 수정이든 동일 로직
    @Transactional
    public FloorGridResponse createOrRegenerateGrid(UUID floorId, CreateOrUpdateFloorGridRequest request) {
        Floor floor = floorRepository.findByIdForUpdate(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        validateFloorReady(floor);

        long columnsLong = (long) Math.ceil(floor.getRealWidth() / request.cellSizeMeter());
        long rowsLong = (long) Math.ceil(floor.getRealHeight() / request.cellSizeMeter());
        validateGridSize(rowsLong, columnsLong);

        int columns = (int) Math.ceil(floor.getRealWidth() / request.cellSizeMeter());
        int rows = (int) Math.ceil(floor.getRealHeight() / request.cellSizeMeter());
        validateGridSize(rows, columns);

        // 기존 그리드 셀 삭제 -> DB FK CASCADE로 NodeGridCell, MapEdgeGridCell 함께 삭제
        floorGridCellRepository.deleteAllByFloorId(floorId);

        // 사용자 지정 구역 삭제 (FloorGridCell의 부모 쪽 참조라 CASCADE로 안 지워지므로 명시적 삭제)
        userZoneRepository.deleteAllByFloorId(floorId);

        // CCTV 노드 삭제 -> 연결된 MapEdge, NodeGridCell도 FK CASCADE로 함께 정리됨
        // (유도등은 CustomDeviceType.GUIDE_LIGHT라 대상에서 제외, 그대로 유지)
        mapNodeRepository.deleteAllByFloorIdAndCustomDeviceType(floorId, CustomDeviceType.CCTV);

        List<FloorGridCell> cells = buildCells(floor, rows, columns, request);
        floorGridCellRepository.saveAll(cells);

        // 살아남은 노드(STAIR/ROOM/HALLWAY/DOOR/EXIT, 유도등) 새 그리드에 재매핑
        List<MapNode> survivingNodes = mapNodeRepository.findAllByFloor_Id(floorId);
        remapNodesToGrid(survivingNodes, cells, rows, columns);

        // 살아남은 엣지가 지나가는 셀 목록 재계산
        List<MapEdge> edges = mapEdgeRepository.findAllByFloor_Id(floorId);
        remapEdgesToGrid(edges, cells, rows, columns);

        // Floor에 최종 그리드 설정 반영
        floor.applyGridCellConfig(request.cellSizeMeter(), rows, columns);
        Floor savedFloor = floorRepository.save(floor);

        return FloorGridResponse.of(savedFloor);
    }

    private void validateFloorReady(Floor floor) {
        if (floor.getSegmentationStatus() != SegmentationStatus.DONE
                || floor.getRealWidth() == null || floor.getRealHeight() == null) {
            throw new ApiException(GridErrorCode.FLOOR_NOT_READY_FOR_GRID);
        }
    }

    private void validateGridSize(long rows, long columns) {
        if (rows <= 0 || columns <= 0) {
            throw new ApiException(GridErrorCode.INVALID_CELL_SIZE);
        }
        long totalCells = rows * columns;
        if (totalCells > MAX_GRID_CELL_COUNT) {
            throw new ApiException(GridErrorCode.TOO_MANY_GRID_CELLS);
        }
    }

    private List<FloorGridCell> buildCells(Floor floor, int rows, int columns,
                                           CreateOrUpdateFloorGridRequest request) {
        List<FloorGridCell> cells = new ArrayList<>(rows * columns);
        double cellWidthNorm = request.cellSizeMeter() / floor.getRealWidth();
        double cellHeightNorm = request.cellSizeMeter() / floor.getRealHeight();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                double centerX = (col + 0.5) * cellWidthNorm;
                double centerY = (row + 0.5) * cellHeightNorm;
                // walkable은 일단 true로 고정 (세그멘테이션 마스크 연동은 추후 반영 예정)
                cells.add(FloorGridCell.create(floor, row, col, true, centerX, centerY));
            }
        }
        return cells;
    }

    // 노드 좌표(0~1 정규화)를 새 그리드의 row/column으로 환산해서 NodeGridCell 재생성
    private void remapNodesToGrid(List<MapNode> nodes, List<FloorGridCell> cells, int rows, int columns) {
        if (nodes.isEmpty()) {
            return;
        }
        FloorGridCell[][] grid = toGridArray(cells, rows, columns);
        List<NodeGridCell> mappings = new ArrayList<>();
        for (MapNode node : nodes) {
            int col = clamp((int) (node.getX() * columns), columns);
            int row = clamp((int) (node.getY() * rows), rows);
            mappings.add(NodeGridCell.create(node, grid[row][col]));
        }
        nodeGridCellRepository.saveAll(mappings);
    }

    // 엣지가 지나는 셀들을 계산해서 MapEdgeGridCell 재생성
    private void remapEdgesToGrid(List<MapEdge> edges, List<FloorGridCell> cells, int rows, int columns) {
        if (edges.isEmpty()) {
            return;
        }
        FloorGridCell[][] grid = toGridArray(cells, rows, columns);
        List<MapEdgeGridCell> mappings = new ArrayList<>();

        for (MapEdge edge : edges) {
            int fromCol = clamp((int) (edge.getFromNode().getX() * columns), columns);
            int fromRow = clamp((int) (edge.getFromNode().getY() * rows), rows);
            int toCol = clamp((int) (edge.getToNode().getX() * columns), columns);
            int toRow = clamp((int) (edge.getToNode().getY() * rows), rows);

            for (int[] cellIdx : bresenhamLine(fromRow, fromCol, toRow, toCol)) {
                mappings.add(MapEdgeGridCell.create(edge, grid[cellIdx[0]][cellIdx[1]]));
            }
        }
        mapEdgeGridCellRepository.saveAll(mappings);
    }

    private FloorGridCell[][] toGridArray(List<FloorGridCell> cells, int rows, int columns) {
        FloorGridCell[][] grid = new FloorGridCell[rows][columns];
        for (FloorGridCell cell : cells) {
            grid[cell.getRowIndex()][cell.getColumnIndex()] = cell;
        }
        return grid;
    }

    private int clamp(int value, int upperExclusive) {
        return Math.max(0, Math.min(value, upperExclusive - 1));
    }

    // 두 셀 좌표 사이를 지나는 셀 목록 계산 (표준 정수 Bresenham 알고리즘)
    private List<int[]> bresenhamLine(int r0, int c0, int r1, int c1) {
        List<int[]> result = new ArrayList<>();
        int dr = Math.abs(r1 - r0), dc = Math.abs(c1 - c0);
        int sr = r0 < r1 ? 1 : -1, sc = c0 < c1 ? 1 : -1;
        int err = dr - dc;

        int r = r0, c = c0;
        while (true) {
            result.add(new int[]{r, c});
            if (r == r1 && c == c1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dc) {
                err -= dc;
                r += sr;
            }
            if (e2 < dr) {
                err += dr;
                c += sc;
            }
        }
        return result;
    }
}
