package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.Cctv;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CctvJpaRepository extends JpaRepository<Cctv, UUID> {

    // 층 조회는 customNode 를 경유한다 (Cctv 에 floor 컬럼이 없음)
    List<Cctv> findAllByCustomNode_Floor_Id(UUID floorId);

    // 라즈베리파이가 보내온 code 로 기기 식별 (전역 unique)
    Optional<Cctv> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Cctv> findByCustomNode_Id(UUID customNodeId);
}