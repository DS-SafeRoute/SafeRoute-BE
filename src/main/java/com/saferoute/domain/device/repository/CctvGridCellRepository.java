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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CctvGridCell mapping where mapping.cctv.id = :cctvId")
    int deleteAllByCctvId(@Param("cctvId") UUID cctvId);
}
