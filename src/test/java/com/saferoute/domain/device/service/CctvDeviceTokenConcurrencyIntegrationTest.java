package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.dto.response.DeviceTokenIssueResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.code.BaseErrorCode;
import com.saferoute.global.api.error.DeviceErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DeviceTokenService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CctvDeviceTokenConcurrencyIntegrationTest {

    @Autowired CctvService cctvService;
    @Autowired DeviceTokenService deviceTokenService;
    @Autowired CctvJpaRepository cctvJpaRepository;
    @Autowired MapNodeJpaRepository mapNodeJpaRepository;
    @Autowired FloorRepository floorRepository;
    @Autowired BuildingRepository buildingRepository;

    @AfterEach
    void cleanUp() {
        cctvJpaRepository.deleteAllInBatch();
        mapNodeJpaRepository.deleteAllInBatch();
        floorRepository.deleteAllInBatch();
        buildingRepository.deleteAllInBatch();
    }

    @Test
    void onlyOneConcurrentInitialTokenIssueSucceeds() throws Exception {
        UUID cctvId = saveCctvWithoutToken().getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<IssueAttempt> issue = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                return IssueAttempt.success(cctvService.issueDeviceToken(cctvId));
            } catch (ApiException exception) {
                return IssueAttempt.failure(exception.getErrorCode());
            }
        };

        try {
            Future<IssueAttempt> first = executor.submit(issue);
            Future<IssueAttempt> second = executor.submit(issue);
            List<IssueAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(attempts).filteredOn(IssueAttempt::succeeded).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                    .singleElement()
                    .extracting(IssueAttempt::errorCode)
                    .isEqualTo(DeviceErrorCode.DEVICE_TOKEN_ALREADY_ISSUED);

            String issuedRawToken = attempts.stream()
                    .filter(IssueAttempt::succeeded)
                    .findFirst()
                    .orElseThrow()
                    .response()
                    .deviceToken();
            Cctv savedCctv = cctvJpaRepository.findById(cctvId).orElseThrow();
            assertThat(savedCctv.getDeviceTokenHash())
                    .isEqualTo(deviceTokenService.hash(issuedRawToken));
        } finally {
            executor.shutdownNow();
        }
    }

    private Cctv saveCctvWithoutToken() {
        Building building = buildingRepository.saveAndFlush(Building.create(
                "동시성관",
                "서울특별시 안전구 동시성로 123",
                BuildingType.CLASSROOM,
                "SafeRoute School"
        ));
        Floor floor = floorRepository.saveAndFlush(Floor.create(building, 1));
        MapNode node = mapNodeJpaRepository.saveAndFlush(MapNode.createCustom(
                floor,
                "CCTV_LOCK_TEST",
                "동시성 테스트 CCTV",
                0.5,
                0.5,
                CustomDeviceType.CCTV
        ));
        return cctvJpaRepository.saveAndFlush(Cctv.create(
                "CCTV_LOCK_TEST",
                "동시성 테스트 CCTV",
                node
        ));
    }

    private record IssueAttempt(
            DeviceTokenIssueResponse response,
            BaseErrorCode errorCode
    ) {
        static IssueAttempt success(DeviceTokenIssueResponse response) {
            return new IssueAttempt(response, null);
        }

        static IssueAttempt failure(BaseErrorCode errorCode) {
            return new IssueAttempt(null, errorCode);
        }

        boolean succeeded() {
            return response != null;
        }
    }
}
