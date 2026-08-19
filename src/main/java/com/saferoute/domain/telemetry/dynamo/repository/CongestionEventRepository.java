package com.saferoute.domain.telemetry.dynamo.repository;

import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.EventProcessingStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
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
public class CongestionEventRepository {

    static final int DEFAULT_QUERY_LIMIT = 100;

    private static final Expression ITEM_NOT_EXISTS = Expression.builder()
            .expression("attribute_not_exists(#pk)")
            .putExpressionName("#pk", "pk")
            .build();

    private final DynamoDbTable<CongestionEventItem> table;
    private final DynamoDbIndex<CongestionEventItem> gsi1;

    public CongestionEventRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName,
            @Value("${aws.dynamodb.gsi1-name:GSI1}") String gsi1Name
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(CongestionEventItem.class));
        this.gsi1 = table.index(gsi1Name);
    }

    public IdempotentSaveResult<CongestionEventItem> saveReceivedIfAbsent(CongestionEventItem item) {
        PutItemEnhancedRequest<CongestionEventItem> request =
                PutItemEnhancedRequest.builder(CongestionEventItem.class)
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

    public Optional<CongestionEventItem> findByEventId(String eventId) {
        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(Key.builder()
                        .partitionValue(CongestionEventItem.buildPk(eventId))
                        .sortValue("META")
                        .build())
                .consistentRead(true)
                .build();
        return Optional.ofNullable(table.getItem(request));
    }

    public List<CongestionEventItem> findAllBySessionId(String trainingSessionId) {
        return findAllBySessionId(trainingSessionId, DEFAULT_QUERY_LIMIT);
    }

    public List<CongestionEventItem> findAllBySessionId(String trainingSessionId, int limit) {
        validateLimit(limit);
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(CongestionEventItem.buildGsi1Pk(trainingSessionId))
                        .build()))
                .scanIndexForward(true)
                .limit(limit)
                .build();

        return gsi1.query(request).stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .toList();
    }

    public boolean updateEventStatus(
            String eventId,
            EventProcessingStatus expectedStatus,
            EventProcessingStatus newStatus
    ) {
        validateEventStatusTransition(expectedStatus, newStatus);
        return updateItem(eventId, "eventStatus", expectedStatus.name(), item -> item.setEventStatus(newStatus));
    }

    public boolean updateImageUploadStatus(
            String eventId,
            ImageUploadStatus expectedStatus,
            ImageUploadStatus newStatus
    ) {
        validateImageStatusTransition(expectedStatus, newStatus);
        return updateItem(
                eventId,
                "imageUploadStatus",
                expectedStatus.name(),
                item -> item.setImageUploadStatus(newStatus)
        );
    }

    private boolean updateItem(
            String eventId,
            String statusAttribute,
            String expectedStatus,
            java.util.function.Consumer<CongestionEventItem> update
    ) {
        CongestionEventItem item = new CongestionEventItem();
        item.setPk(CongestionEventItem.buildPk(eventId));
        item.setSk("META");
        update.accept(item);
        Expression condition = Expression.builder()
                .expression("#status = :expectedStatus")
                .putExpressionName("#status", statusAttribute)
                .putExpressionValue(":expectedStatus", AttributeValue.fromS(expectedStatus))
                .build();
        UpdateItemEnhancedRequest<CongestionEventItem> request =
                UpdateItemEnhancedRequest.builder(CongestionEventItem.class)
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

    private void validateEventStatusTransition(
            EventProcessingStatus expectedStatus,
            EventProcessingStatus newStatus
    ) {
        boolean retryOrStart = (expectedStatus == EventProcessingStatus.RECEIVED
                || expectedStatus == EventProcessingStatus.FAILED)
                && newStatus == EventProcessingStatus.PROCESSING;
        boolean finish = expectedStatus == EventProcessingStatus.PROCESSING
                && (newStatus == EventProcessingStatus.PROCESSED
                || newStatus == EventProcessingStatus.FAILED);
        if (!retryOrStart && !finish) {
            throw new IllegalArgumentException(
                    "허용되지 않은 이벤트 상태 변경입니다: " + expectedStatus + " -> " + newStatus
            );
        }
    }

    private void validateImageStatusTransition(
            ImageUploadStatus expectedStatus,
            ImageUploadStatus newStatus
    ) {
        boolean complete = expectedStatus == ImageUploadStatus.PENDING
                && (newStatus == ImageUploadStatus.COMPLETED || newStatus == ImageUploadStatus.FAILED);
        boolean retry = expectedStatus == ImageUploadStatus.FAILED
                && newStatus == ImageUploadStatus.PENDING;
        if (!complete && !retry) {
            throw new IllegalArgumentException(
                    "허용되지 않은 이미지 상태 변경입니다: " + expectedStatus + " -> " + newStatus
            );
        }
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 0보다 커야합니다.");
        }
    }
}
