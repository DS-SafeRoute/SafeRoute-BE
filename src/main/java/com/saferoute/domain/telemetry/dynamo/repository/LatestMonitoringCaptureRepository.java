package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.LatestMonitoringCaptureItem;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class LatestMonitoringCaptureRepository {

    private final DynamoDbTable<LatestMonitoringCaptureItem> table;

    public LatestMonitoringCaptureRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(LatestMonitoringCaptureItem.class)
        );
    }

    public boolean updateIfLatest(LatestMonitoringCaptureItem incoming) {
        Expression condition = Expression.builder()
                .expression("attribute_not_exists(#capturedAt) OR :incomingCapturedAt >= #capturedAt")
                .putExpressionName("#capturedAt", "capturedAt")
                .putExpressionValue(
                        ":incomingCapturedAt",
                        AttributeValue.fromN(Long.toString(incoming.getCapturedAt()))
                )
                .build();
        PutItemEnhancedRequest<LatestMonitoringCaptureItem> request =
                PutItemEnhancedRequest.builder(LatestMonitoringCaptureItem.class)
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

    public List<LatestMonitoringCaptureItem> findAllBySessionId(String trainingSessionId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(LatestMonitoringCaptureItem.buildPk(trainingSessionId))
                        .build()))
                .scanIndexForward(true)
                .build();
        return table.query(request).items().stream().toList();
    }
}
