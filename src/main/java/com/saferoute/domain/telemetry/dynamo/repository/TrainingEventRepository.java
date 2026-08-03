package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.TrainingEventItem;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class TrainingEventRepository {

    private static final String SORT_KEY_PREFIX = "EVENT#";

    private final DynamoDbTable<TrainingEventItem> table;

    public TrainingEventRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(TrainingEventItem.class)
        );
    }

    public void save(TrainingEventItem item) {
        table.putItem(item);
    }

    public List<TrainingEventItem> findAllBySessionId(String sessionId) {
        QueryConditional condition = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(TrainingEventItem.buildPk(sessionId))
                        .sortValue(SORT_KEY_PREFIX)
                        .build()
        );

        return table.query(condition)
                .items()
                .stream()
                .toList();
    }

    public void delete(TrainingEventItem item) {
        table.deleteItem(
                Key.builder()
                        .partitionValue(item.getPk())
                        .sortValue(item.getSk())
                        .build()
        );
    }
}