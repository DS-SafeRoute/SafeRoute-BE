package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.CctvGridCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CctvGridCellRepository extends JpaRepository<CctvGridCell, UUID> {

    List<CctvGridCell> findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(UUID cctvId);

    // 특정 GridCell들을 감시하는 CCTV를 역으로 찾는다 (Edge -> GridCell -> CCTV 조회, 경로 이탈률 계산에 사용).
    List<CctvGridCell> findAllByGridCell_IdIn(List<UUID> gridCellIds);

    // 감시 면적 계산엔 셀 목록 전체가 아니라 개수만 필요하다 (Pi 설정 조회 API).
    int countByCctv_Id(UUID cctvId);

    @Query("""
            select mapping
            from CctvGridCell mapping
            join fetch mapping.cctv cctv
            join fetch mapping.gridCell cell
            where cctv.id in :cctvIds
            order by cctv.id, cell.rowIndex, cell.columnIndex
            """)
    List<CctvGridCell> findAllByCctvIdsWithGridCell(@Param("cctvIds") List<UUID> cctvIds);

    // clearAutomatically는 쓰지 않는다 - 호출 직후 configureGridCells가 같은 트랜잭션에서
    // cctv/gridCells 엔티티를 계속 참조하는데, clear()가 영속성 컨텍스트를 비우면 그 엔티티들이
    // detach되어 이후 새 CctvGridCell 저장 시 예기치 않은 동작을 일으킬 수 있다
    // (MapEdgeGridCellRepository.deleteAllByMapEdgeId와 동일한 이슈, #228 참고).
    @Modifying(flushAutomatically = true)
    @Query("delete from CctvGridCell mapping where mapping.cctv.id = :cctvId")
    int deleteAllByCctvId(@Param("cctvId") UUID cctvId);
}
