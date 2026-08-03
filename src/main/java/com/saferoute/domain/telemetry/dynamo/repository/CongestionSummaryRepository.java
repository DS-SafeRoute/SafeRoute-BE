package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.CongestionSummaryItem;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class CongestionSummaryRepository {

    private static final String SORT_KEY_PREFIX = "CONGESTION_SUMMARY#";
    static final int DEFAULT_QUERY_LIMIT = 100;

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
        return findAllBySessionId(sessionId, DEFAULT_QUERY_LIMIT);
    }

    public List<CongestionSummaryItem> findAllBySessionId(String sessionId, int limit) {
        QueryConditional condition = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(CongestionSummaryItem.buildPk(sessionId))
                        .sortValue(SORT_KEY_PREFIX)
                        .build()
        );

        return queryWithLimit(condition, limit);
    }

    public List<CongestionSummaryItem> findAllBySessionIdAndEdgeId(
            String sessionId,
            String edgeId
    ) {
        return findAllBySessionIdAndEdgeId(sessionId, edgeId, DEFAULT_QUERY_LIMIT);
    }

    public List<CongestionSummaryItem> findAllBySessionIdAndEdgeId(
            String sessionId,
            String edgeId,
            int limit
    ) {
        String edgePrefix = SORT_KEY_PREFIX + edgeId + "#";

        QueryConditional condition = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(CongestionSummaryItem.buildPk(sessionId))
                        .sortValue(edgePrefix)
                        .build()
        );

        return queryWithLimit(condition, limit);
    }

    public void delete(CongestionSummaryItem item) {
        table.deleteItem(
                Key.builder()
                        .partitionValue(item.getPk())
                        .sortValue(item.getSk())
                        .build()
        );
    }

    private List<CongestionSummaryItem> queryWithLimit(
            QueryConditional condition,
            int limit
    ) {
        validateLimit(limit);

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .limit(limit)
                .build();

        return table.query(request)
                .items()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 0보다 커야합니다.");
        }
    }
}
