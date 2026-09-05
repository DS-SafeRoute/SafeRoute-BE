package com.saferoute.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class FloorAnalysisStatusConcurrencyIntegrationTest {

    private static final String SCHOOL_NAME = "Lock Test School";

    @Autowired
    private FloorAnalysisStatusService statusService;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private FloorRepository floorRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentProcessingRequests_allowOnlyOneClaim() throws Exception {
        UUID floorId = transactionTemplate.execute(status -> createReadyFloor());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(
                    () -> claimResult(floorId, ready, start));
            Future<String> second = executor.submit(
                    () -> claimResult(floorId, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "ANALYSIS003");
            assertThat(floorRepository.findById(floorId).orElseThrow().getSegmentationStatus())
                    .isEqualTo(SegmentationStatus.PROCESSING);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private String claimResult(UUID floorId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("분석 시작 동시 요청 신호를 기다리는 중 시간 초과되었습니다.");
        }
        try {
            statusService.markAsProcessing(floorId, SCHOOL_NAME);
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getErrorCode().getCode();
        }
    }

    private UUID createReadyFloor() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Building building = buildingRepository.save(Building.create(
                "분석 잠금관 " + suffix,
                "서울특별시 안전구 " + suffix,
                BuildingType.CLASSROOM,
                SCHOOL_NAME));
        Floor floor = Floor.create(building, 1);
        floor.upload(3.0, 4.0, "floors/analysis-lock.png");
        floor.updateSegmentationStatus(SegmentationStatus.DONE);
        return floorRepository.save(floor).getId();
    }
}
