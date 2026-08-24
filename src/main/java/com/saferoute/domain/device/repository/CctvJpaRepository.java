package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.Cctv;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CctvJpaRepository extends JpaRepository<Cctv, UUID> {

    // 층 조회는 customNode 를 경유한다 (Cctv 에 floor 컬럼이 없음)
    @EntityGraph(attributePaths = {"customNode", "customNode.floor"})
    List<Cctv> findAllByCustomNode_Floor_Id(UUID floorId);

    @EntityGraph(attributePaths = {"customNode", "customNode.floor"})
    @Query("select cctv from Cctv cctv")
    List<Cctv> findAllWithLocation();

    @EntityGraph(attributePaths = {"customNode", "customNode.floor"})
    @Query("select cctv from Cctv cctv where cctv.id = :cctvId")
    Optional<Cctv> findByIdWithLocation(@Param("cctvId") UUID cctvId);

    // 인증 서비스의 트랜잭션이 끝난 뒤 혼잡 설정 조회에서 위치 정보를 사용하므로 함께 초기화한다.
    // 라즈베리파이가 보내온 code 로 기기 식별 (전역 unique)
    @EntityGraph(attributePaths = {"customNode", "customNode.floor", "customNode.floor.building"})
    Optional<Cctv> findByCode(String code);

    Optional<Cctv> findByDeviceTokenHash(String deviceTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cctv from Cctv cctv where cctv.id = :cctvId")
    Optional<Cctv> findByIdForDeviceTokenIssue(@Param("cctvId") UUID cctvId);

    boolean existsByCode(String code);

    Optional<Cctv> findByCustomNode_Id(UUID customNodeId);
}
