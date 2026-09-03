package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.ObservationCountItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class ObservationCountRepository {

    private static final String SORT_KEY = "META";

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<ObservationCountItem> table;
    private final String tableName;
    private final String gsi1Name;

    public ObservationCountRepository(
            DynamoDbClient dynamoDbClient,
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.dynamodb.gsi1-name:GSI1}") String gsi1Name
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ObservationCountItem.class));
        this.tableName = tableName;
        this.gsi1Name = gsi1Name;
    }

    // 세션+CCTV의 Observation 저장 개수를 1 증가시킨다. Enhanced Client는 임의의 update expression을
    // 지원하지 않으므로, 저수준 DynamoDbClient로 ADD 원자 연산을 직접 호출한다 - 동시 요청이 들어와도
    // 읽고-계산해서-쓰는 방식이 아니라 DynamoDB 서버 사이드에서 정확히 합산된다.
    public void increment(String trainingSessionId, String cctvCode) {
        Map<String, AttributeValue> key = Map.of(
                "pk", AttributeValue.fromS(ObservationCountItem.buildPk(trainingSessionId, cctvCode)),
                "sk", AttributeValue.fromS(SORT_KEY)
        );
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("ADD #count :incr")
                .expressionAttributeNames(Map.of("#count", "count"))
                .expressionAttributeValues(Map.of(":incr", AttributeValue.fromN("1")))
                .build());
    }

    public Optional<Long> find(String trainingSessionId, String cctvCode) {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(Key.builder()
                        .partitionValue(ObservationCountItem.buildPk(trainingSessionId, cctvCode))
                        .sortValue(SORT_KEY)
                        .build())
                .consistentRead(true)
                .build();
        return Optional.ofNullable(table.getItem(request)).map(ObservationCountItem::getCount);
    }

    // 카운터 아이템이 없는(이 기능 배포 이전에 생성된) 세션+CCTV 조합을 위한 fallback.
    // Select=COUNT로 아이템 본문을 가져오지 않고 개수만 세어, ObservationItem의 GSI1 파티션
    // (세션+CCTV 단위, buildGsi1Pk와 동일 기준) 전체를 순회한다.
    public long countAllBySessionIdAndCctvCode(String trainingSessionId, String cctvCode) {
        Map<String, AttributeValue> values = Map.of(
                ":gsi1pk", AttributeValue.fromS(ObservationItem.buildGsi1Pk(trainingSessionId, cctvCode))
        );

        long total = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;
        do {
            QueryRequest.Builder requestBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(gsi1Name)
                    .keyConditionExpression("GSI1_PK = :gsi1pk")
                    .expressionAttributeValues(values)
                    .select(Select.COUNT);
            if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                requestBuilder.exclusiveStartKey(lastEvaluatedKey);
            }
            QueryResponse response = dynamoDbClient.query(requestBuilder.build());
            total += response.count();
            lastEvaluatedKey = response.lastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return total;
    }
}
