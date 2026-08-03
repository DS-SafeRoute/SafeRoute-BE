package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.TrainingTelemetryItem;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class TrainingTelemetryRepository {

    private static final String SORT_KEY_PREFIX = "TELEMETRY#";
    static final int DEFAULT_QUERY_LIMIT = 100;

    private final DynamoDbTable<TrainingTelemetryItem> table;

    public TrainingTelemetryRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(TrainingTelemetryItem.class)
        );
    }

    public void save(TrainingTelemetryItem item) {
        table.putItem(item);
    }

    public List<TrainingTelemetryItem> findAllBySessionId(String sessionId) {
        return findAllBySessionId(sessionId, DEFAULT_QUERY_LIMIT);
    }

    public List<TrainingTelemetryItem> findAllBySessionId(
            String sessionId,
            int limit
    ) {
        QueryConditional condition = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(TrainingTelemetryItem.buildPk(sessionId))
                        .sortValue(SORT_KEY_PREFIX)
                        .build()
        );

        validateLimit(limit);

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .limit(limit)
                .build();

        return table.query(request)
                .stream()
                .findFirst()
                .map(page -> page.items())
                .orElseGet(List::of);
    }

    public void delete(TrainingTelemetryItem item) {
        table.deleteItem(
                Key.builder()
                        .partitionValue(item.getPk())
                        .sortValue(item.getSk())
                        .build()
        );
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be greater than zero"
            );
        }
    }
}