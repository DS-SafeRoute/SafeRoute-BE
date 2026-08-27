package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FloorGridCellRepository extends JpaRepository<FloorGridCell, UUID> {

    List<FloorGridCell> findAllByFloor_Id(UUID floorId);

    Page<FloorGridCell> findAllByFloor_Id(UUID floorId, Pageable pageable);

    // 화재 확산 시 rowIndex/columnIndex 로 인접 셀 탐색
    Optional<FloorGridCell> findByFloor_IdAndRowIndexAndColumnIndex(UUID floorId, int rowIndex, int columnIndex);

    // 현재 화재 중인 셀 (확산 시뮬레이션 tick 대상)
    List<FloorGridCell> findAllByFloor_IdAndIsFiredTrue(UUID floorId);

    // 사용자 지정 구역에 속한 셀 목록 (UserZone 단방향이므로 여기서 조회)
    List<FloorGridCell> findAllByUserZone_Id(UUID userZoneId);

    // 훈련 종료 시 화재 상태 일괄 초기화.
    // FloorGridCell.fired 는 정적 도면 데이터에 얹은 동적 상태이므로 반드시 호출해야 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FloorGridCell c set c.isFired = false where c.floor.id = :floorId and c.isFired = true")
    int resetFiredByFloorId(@Param("floorId") UUID floorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FloorGridCell c where c.floor.id = :floorId")
    int deleteAllByFloorId(@Param("floorId") UUID floorId);

    // 4방향(상하좌우) 인접 셀. 대각선까지 필요하면 조건 추가.
    @Query("""
        SELECT c FROM FloorGridCell c
        WHERE c.floor.id = :floorId
          AND (
            (c.rowIndex = :row - 1 AND c.columnIndex = :column) OR
            (c.rowIndex = :row + 1 AND c.columnIndex = :column) OR
            (c.rowIndex = :row AND c.columnIndex = :column - 1) OR
            (c.rowIndex = :row AND c.columnIndex = :column + 1)
          )
        """)
    List<FloorGridCell> findAdjacent(@Param("floorId") UUID floorId,
                                     @Param("row") int row,
                                     @Param("column") int column);
}
