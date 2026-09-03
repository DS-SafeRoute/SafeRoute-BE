package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventItem;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class GeneralMonitoringEventRepository {

    private static final Expression ITEM_NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(#pk)")
            .putExpressionName("#pk", "pk")
            .build();

    private final DynamoDbTable<GeneralMonitoringEventItem> table;
    private final DynamoDbIndex<GeneralMonitoringEventItem> gsi1;

    public GeneralMonitoringEventRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.dynamodb.gsi1-name:GSI1}") String gsi1Name
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(GeneralMonitoringEventItem.class));
        this.gsi1 = table.index(gsi1Name);
    }

    // eventId를 결정적으로 생성해 넘기면(예: 세션+CCTV+이벤트타입 조합) 이 조건부 put 하나가
    // "세션+CCTV당 정확히 한 번" 같은 멱등 규칙을 그대로 보장한다. 별도의 마커 아이템이나
    // 인메모리 상태를 두지 않는다.
    public IdempotentSaveResult<GeneralMonitoringEventItem> saveIfAbsent(GeneralMonitoringEventItem item) {
        PutItemEnhancedRequest<GeneralMonitoringEventItem> request =
                PutItemEnhancedRequest.builder(GeneralMonitoringEventItem.class)
                        .item(item)
                        .conditionExpression(ITEM_NOT_EXISTS)
                        .build();

        try {
            table.putItem(request);
            return IdempotentSaveResult.created(item);
        } catch (ConditionalCheckFailedException exception) {
            return findByEventId(item.getEventId())
                    .map(IdempotentSaveResult::existing)
                    .orElseThrow(() -> exception);
        }
    }

    public Optional<GeneralMonitoringEventItem> findByEventId(String eventId) {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(Key.builder()
                        .partitionValue(GeneralMonitoringEventItem.buildPk(eventId))
                        .sortValue("META")
                        .build())
                .consistentRead(true)
                .build();
        return Optional.ofNullable(table.getItem(request));
    }

    // 이벤트 타임라인 조회 전용: CongestionEventRepository.findPageBySessionId와 동일한 패턴으로
    // 최신순 커서 페이지네이션 + cctvCode DynamoDB FilterExpression을 적용한다. Limit을 요청 단위와
    // 스트림 단위 양쪽에 걸어, cctvCode로 걸러진 만큼 SDK가 다음 물리 페이지를 계속 읽어오게 한다.
    public List<GeneralMonitoringEventItem> findPageBySessionId(
            String trainingSessionId,
            String cctvCode,
            int limit,
            Long beforeOccurredAt,
            String beforeEventId
    ) {
        validateLimit(limit);
        QueryConditional queryConditional = beforeOccurredAt == null
                ? QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(GeneralMonitoringEventItem.buildGsi1Pk(trainingSessionId))
                        .build())
                : QueryConditional.sortLessThan(Key.builder()
                        .partitionValue(GeneralMonitoringEventItem.buildGsi1Pk(trainingSessionId))
                        .sortValue(GeneralMonitoringEventItem.buildGsi1Sk(beforeOccurredAt, beforeEventId))
                        .build());
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .limit(limit);
        if (cctvCode != null) {
            requestBuilder.filterExpression(Expression.builder()
                    .expression("#cctvCode = :cctvCode")
                    .putExpressionName("#cctvCode", "cctvCode")
                    .putExpressionValue(":cctvCode", AttributeValue.fromS(cctvCode))
                    .build());
        }

        return gsi1.query(requestBuilder.build()).stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .toList();
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 0보다 커야합니다.");
        }
    }
}
