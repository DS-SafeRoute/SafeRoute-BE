package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.LightDirectionEventItem;
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
public class LightDirectionEventRepository {

    static final int DEFAULT_QUERY_LIMIT = 500;

    private final DynamoDbTable<LightDirectionEventItem> table;

    public LightDirectionEventRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(LightDirectionEventItem.class));
    }

    public void save(LightDirectionEventItem item) {
        table.putItem(item);
    }

    // 세션 내 한 유도등의 방향 전환 이력을 시간순으로 조회한다 (경로 이탈률 계산 시 특정 시점의 활성 방향을 찾는 데 사용).
    public List<LightDirectionEventItem> findAllBySessionIdAndLightCode(String trainingSessionId, String lightCode) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(LightDirectionEventItem.buildPk(trainingSessionId, lightCode))
                        .build()))
                .scanIndexForward(true)
                .limit(DEFAULT_QUERY_LIMIT)
                .build();

        return table.query(request).items().stream().toList();
    }
}
