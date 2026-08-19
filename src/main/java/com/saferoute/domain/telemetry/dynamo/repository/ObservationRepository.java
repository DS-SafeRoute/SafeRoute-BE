package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.EventProcessingStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
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
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class ObservationRepository {

    static final int DEFAULT_QUERY_LIMIT = 100;

    private static final Expression ITEM_NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(#pk)")
            .putExpressionName("#pk", "pk")
            .build();

    private final DynamoDbTable<ObservationItem> table;
    private final DynamoDbIndex<ObservationItem> gsi1;

    public ObservationRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.dynamodb.gsi1-name:GSI1}") String gsi1Name
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ObservationItem.class));
        this.gsi1 = table.index(gsi1Name);
    }

    public IdempotentSaveResult<ObservationItem> saveIfAbsent(ObservationItem item) {
        PutItemEnhancedRequest<ObservationItem> request = PutItemEnhancedRequest.builder(ObservationItem.class)
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

    public Optional<ObservationItem> findByEventId(String eventId) {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(Key.builder()
                        .partitionValue(ObservationItem.buildPk(eventId))
                        .sortValue("META")
                        .build())
                .consistentRead(true)
                .build();
        return Optional.ofNullable(table.getItem(request));
    }

    public boolean claimProcessing(
            String eventId,
            String processingOwner,
            long processingStartedAt,
            long processingExpiresAt
    ) {
        ObservationItem item = processingUpdateItem(eventId);
        item.setEventStatus(EventProcessingStatus.PROCESSING);
        item.setProcessingOwner(processingOwner);
        item.setProcessingStartedAt(processingStartedAt);
        item.setProcessingExpiresAt(processingExpiresAt);

        Expression condition = Expression.builder()
                .expression("attribute_not_exists(#status)"
                        + " OR #status = :received"
                        + " OR #status = :failed"
                        + " OR (#status = :processing AND #processingExpiresAt <= :now)")
                .putExpressionName("#status", "eventStatus")
                .putExpressionName("#processingExpiresAt", "processingExpiresAt")
                .putExpressionValue(":received", AttributeValue.fromS("RECEIVED"))
                .putExpressionValue(":failed", AttributeValue.fromS("FAILED"))
                .putExpressionValue(":processing", AttributeValue.fromS("PROCESSING"))
                .putExpressionValue(":now", AttributeValue.fromN(Long.toString(processingStartedAt)))
                .build();
        return updateConditionally(item, condition);
    }

    public boolean completeProcessing(String eventId, String processingOwner) {
        return finishProcessing(
                eventId,
                processingOwner,
                EventProcessingStatus.PROCESSED
        );
    }

    public boolean failProcessing(String eventId, String processingOwner) {
        return finishProcessing(
                eventId,
                processingOwner,
                EventProcessingStatus.FAILED
        );
    }

    public List<ObservationItem> findAllBySessionIdAndCctvCode(
            String trainingSessionId,
            String cctvCode
    ) {
        return findAllBySessionIdAndCctvCode(trainingSessionId, cctvCode, DEFAULT_QUERY_LIMIT);
    }

    public List<ObservationItem> findAllBySessionIdAndCctvCode(
            String trainingSessionId,
            String cctvCode,
            int limit
    ) {
        validateLimit(limit);
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(ObservationItem.buildGsi1Pk(trainingSessionId, cctvCode))
                        .build()))
                .scanIndexForward(true)
                .limit(limit)
                .build();

        return gsi1.query(request).stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .toList();
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 0보다 커야합니다.");
        }
    }

    private boolean finishProcessing(
            String eventId,
            String processingOwner,
            EventProcessingStatus status
    ) {
        ObservationItem item = processingUpdateItem(eventId);
        item.setEventStatus(status);
        Expression condition = Expression.builder()
                .expression("#status = :processing AND #processingOwner = :processingOwner")
                .putExpressionName("#status", "eventStatus")
                .putExpressionName("#processingOwner", "processingOwner")
                .putExpressionValue(":processing", AttributeValue.fromS("PROCESSING"))
                .putExpressionValue(":processingOwner", AttributeValue.fromS(processingOwner))
                .build();
        return updateConditionally(item, condition);
    }

    private ObservationItem processingUpdateItem(String eventId) {
        ObservationItem item = new ObservationItem();
        item.setPk(ObservationItem.buildPk(eventId));
        item.setSk("META");
        return item;
    }

    private boolean updateConditionally(ObservationItem item, Expression condition) {
        UpdateItemEnhancedRequest<ObservationItem> request =
                UpdateItemEnhancedRequest.builder(ObservationItem.class)
                        .item(item)
                        .ignoreNullsMode(IgnoreNullsMode.SCALAR_ONLY)
                        .conditionExpression(condition)
                        .build();
        try {
            table.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }
}
