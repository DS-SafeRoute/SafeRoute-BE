package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.TrainingTelemetryItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@EnabledIfEnvironmentVariable(
        named = "RUN_AWS_INTEGRATION_TESTS",
        matches = "true"
)
class TrainingTelemetryRepositoryAwsIntegrationTest {

    @Test
    void 실제_DynamoDB에_텔레메트리를_저장하고_조회하고_삭제한다() {
        String regionName =
                System.getenv().getOrDefault("AWS_REGION", "us-east-2");
        String tableName =
                System.getenv().getOrDefault(
                        "DYNAMODB_TABLE",
                        "saferoute-telemetry"
                );

        try (DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(regionName))
                .build()) {

            DynamoDbEnhancedClient enhancedClient =
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDbClient)
                            .build();

            TrainingTelemetryRepository repository =
                    new TrainingTelemetryRepository(
                            enhancedClient,
                            tableName
                    );

            String sessionId = "integration-" + UUID.randomUUID();
            String cameraCode = "CCTV-TEST";
            long timestamp = Instant.now().getEpochSecond();
            long expiresAt = timestamp + 3600;

            TrainingTelemetryItem item =
                    TrainingTelemetryItem.create(
                            sessionId,
                            cameraCode,
                            timestamp,
                            4,
                            10,
                            0.4,
                            CongestionLevel.LOW,
                            expiresAt
                    );

            try {
                repository.save(item);

                List<TrainingTelemetryItem> result =
                        repository.findAllBySessionId(sessionId);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getSessionId())
                        .isEqualTo(sessionId);
                assertThat(result.get(0).getCameraCode())
                        .isEqualTo(cameraCode);
                assertThat(result.get(0).getPersonCount())
                        .isEqualTo(4);
                assertThat(result.get(0).getExpiresAt())
                        .isEqualTo(expiresAt);
            } finally {
                repository.delete(item);
            }

            List<TrainingTelemetryItem> afterDelete =
                    repository.findAllBySessionId(sessionId);

            assertThat(afterDelete).isEmpty();
        }
    }
}