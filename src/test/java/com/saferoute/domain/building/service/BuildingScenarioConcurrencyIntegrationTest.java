package com.saferoute.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.training.dto.CreateScenarioRequest;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.service.TrainingScenarioService;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BuildingScenarioConcurrencyIntegrationTest {

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private TrainingScenarioService scenarioService;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private TrainingScenarioRepository scenarioRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void scenarioCreationFirstMakesConcurrentBuildingDeletionReturnTrainingHistoryError() throws Exception {
        Fixture fixture = saveFixture();
        CountDownLatch scenarioCreated = new CountDownLatch(1);
        CountDownLatch allowScenarioCommit = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> creation = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    scenarioService.createScenario(fixture.request(), fixture.email());
                    scenarioCreated.countDown();
                    await(allowScenarioCommit);
                });
                return null;
            });
            assertThat(scenarioCreated.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApiException> deletion = executor.submit(() -> {
                deletionStarted.countDown();
                try {
                    buildingService.deleteBuilding(fixture.buildingId(), fixture.email());
                    return null;
                } catch (ApiException exception) {
                    return exception;
                }
            });
            assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            allowScenarioCommit.countDown();

            creation.get(10, TimeUnit.SECONDS);
            ApiException deletionError = deletion.get(10, TimeUnit.SECONDS);

            assertThat(deletionError).isNotNull();
            assertThat(deletionError.getErrorCode())
                    .isEqualTo(BuildingErrorCode.BUILDING_HAS_TRAINING_HISTORY);
            assertThat(buildingRepository.existsById(fixture.buildingId())).isTrue();
            assertThat(scenarioRepository.existsByBuilding_Id(fixture.buildingId())).isTrue();
        } finally {
            allowScenarioCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void buildingDeletionFirstMakesConcurrentScenarioCreationRejectMissingBuilding() throws Exception {
        Fixture fixture = saveFixture();
        CountDownLatch buildingDeleted = new CountDownLatch(1);
        CountDownLatch allowDeletionCommit = new CountDownLatch(1);
        CountDownLatch creationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> deletion = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    buildingService.deleteBuilding(fixture.buildingId(), fixture.email());
                    buildingDeleted.countDown();
                    await(allowDeletionCommit);
                });
                return null;
            });
            assertThat(buildingDeleted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApiException> creation = executor.submit(() -> {
                creationStarted.countDown();
                try {
                    scenarioService.createScenario(fixture.request(), fixture.email());
                    return null;
                } catch (ApiException exception) {
                    return exception;
                }
            });
            assertThat(creationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            allowDeletionCommit.countDown();

            deletion.get(10, TimeUnit.SECONDS);
            ApiException creationError = creation.get(10, TimeUnit.SECONDS);

            assertThat(creationError).isNotNull();
            assertThat(creationError.getErrorCode()).isEqualTo(BuildingErrorCode.BUILDING_NOT_FOUND);
            assertThat(buildingRepository.existsById(fixture.buildingId())).isFalse();
            assertThat(scenarioRepository.existsByBuilding_Id(fixture.buildingId())).isFalse();
        } finally {
            allowDeletionCommit.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture saveFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "manager-" + suffix + "@saferoute.com";
        String schoolName = "SafeRoute School";
        User admin = userRepository.save(User.create(
                "manager" + suffix,
                "encoded-password",
                email,
                UserRole.MANAGER,
                schoolName));
        Building building = buildingRepository.save(Building.create(
                "공학관",
                "서울특별시 성북구 안전로 1",
                BuildingType.CLASSROOM,
                schoolName));
        CreateScenarioRequest request = new CreateScenarioRequest(
                "정기 훈련",
                building.getId(),
                50,
                300,
                Instant.now().plusSeconds(3600),
                false,
                admin.getId(),
                FireSpreadSpeed.MEDIUM);
        return new Fixture(email, building.getId(), request);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 신호를 기다리는 중 시간 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 신호 대기가 중단되었습니다.", exception);
        }
    }

    private record Fixture(String email, UUID buildingId, CreateScenarioRequest request) {
    }
}
