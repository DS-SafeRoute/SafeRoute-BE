package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.CurrentCctvStateItem;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
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
public class CurrentCctvStateRepository {

    private final DynamoDbTable<CurrentCctvStateItem> table;

    public CurrentCctvStateRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(CurrentCctvStateItem.class));
    }

    public boolean updateIfLatest(CurrentCctvStateItem incoming) {
        Expression condition = Expression.builder()
                .expression("attribute_not_exists(#lastDetectedAt) OR :incomingTimestamp >= #lastDetectedAt")
                .putExpressionName("#lastDetectedAt", "lastDetectedAt")
                .putExpressionValue(
                        ":incomingTimestamp",
                        AttributeValue.fromN(Long.toString(incoming.getLastDetectedAt()))
                )
                .build();
        PutItemEnhancedRequest<CurrentCctvStateItem> request =
                PutItemEnhancedRequest.builder(CurrentCctvStateItem.class)
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

    public Optional<CurrentCctvStateItem> findBySessionIdAndCctvCode(
            String trainingSessionId,
            String cctvCode
    ) {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(Key.builder()
                        .partitionValue(CurrentCctvStateItem.buildPk(trainingSessionId))
                        .sortValue(CurrentCctvStateItem.buildSk(cctvCode))
                        .build())
                .consistentRead(true)
                .build();
        return Optional.ofNullable(table.getItem(request));
    }

    public List<CurrentCctvStateItem> findAllBySessionId(String trainingSessionId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(CurrentCctvStateItem.buildPk(trainingSessionId))
                        .build()))
                .scanIndexForward(true)
                .build();
        return table.query(request).items().stream().toList();
    }
}
