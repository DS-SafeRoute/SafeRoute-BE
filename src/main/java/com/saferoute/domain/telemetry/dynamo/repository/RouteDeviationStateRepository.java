package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.RouteDeviationStateItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class RouteDeviationStateRepository {

    private final DynamoDbTable<RouteDeviationStateItem> table;

    public RouteDeviationStateRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(RouteDeviationStateItem.class));
    }

    public Optional<RouteDeviationStateItem> find(String trainingSessionId, UUID lightId) {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(Key.builder()
                        .partitionValue(RouteDeviationStateItem.buildPk(trainingSessionId, lightId.toString()))
                        .sortValue("META")
                        .build())
                .consistentRead(true)
                .build();
        return Optional.ofNullable(table.getItem(request));
    }

    // incoming.lastProcessedCapturedAt이 저장된 값보다 클 때만 성공하는 조건부 쓰기.
    // 상태 전환(NORMAL/DEVIATING)과 lastProcessedCapturedAt 갱신을 하나의 원자적 쓰기로 묶어,
    // 지연 도착/과거 시각의 Observation이 최신 상태를 되돌리지 않도록 막는다.
    public boolean saveIfNewer(RouteDeviationStateItem incoming) {
        Expression condition = Expression.builder()
                .expression("attribute_not_exists(#lastProcessedCapturedAt) OR #lastProcessedCapturedAt < :incoming")
                .putExpressionName("#lastProcessedCapturedAt", "lastProcessedCapturedAt")
                .putExpressionValue(
                        ":incoming",
                        AttributeValue.fromN(Long.toString(incoming.getLastProcessedCapturedAt()))
                )
                .build();
        PutItemEnhancedRequest<RouteDeviationStateItem> request =
                PutItemEnhancedRequest.builder(RouteDeviationStateItem.class)
                        .item(incoming)
                        .conditionExpression(condition)
                        .build();

        try {
            table.putItem(request);
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }
}
