package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.CongestionSummaryItem;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class CongestionSummaryRepository {

    private static final String SORT_KEY_PREFIX = "CONGESTION_SUMMARY#";

    private final DynamoDbTable<CongestionSummaryItem> table;

    public CongestionSummaryRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(CongestionSummaryItem.class)
        );
    }

    public void save(CongestionSummaryItem item) {
        table.putItem(item);
    }

    public List<CongestionSummaryItem> findAllBySessionId(String sessionId) {
        QueryConditional condition = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(CongestionSummaryItem.buildPk(sessionId))
                        .sortValue(SORT_KEY_PREFIX)
                        .build()
        );

        return table.query(condition)
                .items()
                .stream()
                .toList();
    }

    public List<CongestionSummaryItem> findAllBySessionIdAndEdgeId(
            String sessionId,
            String edgeId
    ) {
        String edgePrefix = SORT_KEY_PREFIX + edgeId + "#";

        QueryConditional condition = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(CongestionSummaryItem.buildPk(sessionId))
                        .sortValue(edgePrefix)
                        .build()
        );

        return table.query(condition)
                .items()
                .stream()
                .toList();
    }

    public void delete(CongestionSummaryItem item) {
        table.deleteItem(
                Key.builder()
                        .partitionValue(item.getPk())
                        .sortValue(item.getSk())
                        .build()
        );
    }
}