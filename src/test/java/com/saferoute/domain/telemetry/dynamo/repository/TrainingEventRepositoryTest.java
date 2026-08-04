package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.telemetry.dynamo.entity.EventType;
import com.saferoute.domain.telemetry.dynamo.entity.TrainingEventItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@ExtendWith(MockitoExtension.class)
class TrainingEventRepositoryTest {

    private static final String TABLE_NAME = "saferoute-telemetry";
    private static final TableSchema<TrainingEventItem> TABLE_SCHEMA =
            TableSchema.fromBean(TrainingEventItem.class);

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<TrainingEventItem> table;

    private TrainingEventRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(table);
        repository = new TrainingEventRepository(enhancedClient, TABLE_NAME);
    }

    @Test
    void 훈련_이벤트를_저장한다() {
        TrainingEventItem item = createItem("event-1", 1_000L);

        repository.save(item);

        verify(table).putItem(item);
    }

    @Test
    void 훈련_ID로_이벤트를_여러_페이지에서_읽고_총_limit만큼_제한한다() {
        TrainingEventItem first = createItem("event-1", 1_000L);
        TrainingEventItem second = createItem("event-2", 2_000L);
        TrainingEventItem third = createItem("event-3", 3_000L);
        mockQueryResult(List.of(List.of(first), List.of(second, third)));

        List<TrainingEventItem> result =
                repository.findAllBySessionId("session-1", 2);

        assertThat(result).containsExactly(first, second);
        assertQuery("TRAINING#session-1", "EVENT#", 2);
    }

    @Test
    void 기본_조회_limit은_100이다() {
        TrainingEventItem item = createItem("event-1", 1_000L);
        mockQueryResult(List.of(List.of(item)));

        assertThat(repository.findAllBySessionId("session-1")).containsExactly(item);

        assertQuery("TRAINING#session-1", "EVENT#", 100);
    }

    @Test
    void 조회_결과가_없으면_빈_목록을_반환한다() {
        mockQueryResult(List.of());

        assertThat(repository.findAllBySessionId("session-1")).isEmpty();
    }

    @Test
    void 훈련_이벤트를_삭제할_때_PK와_SK를_전달한다() {
        TrainingEventItem item = createItem("event-1", 1_000L);
        ArgumentCaptor<Key> captor = ArgumentCaptor.forClass(Key.class);

        repository.delete(item);

        verify(table).deleteItem(captor.capture());
        assertThat(captor.getValue().partitionKeyValue().s()).isEqualTo(item.getPk());
        assertThat(captor.getValue().sortKeyValue()).hasValueSatisfying(
                value -> assertThat(value.s()).isEqualTo(item.getSk())
        );
    }

    @Test
    void 저장_중_DynamoDB_예외가_발생하면_그대로_전파한다() {
        TrainingEventItem item = createItem("event-1", 1_000L);
        RuntimeException exception = DynamoDbException.builder().message("put failed").build();
        doThrow(exception).when(table).putItem(item);

        assertThatThrownBy(() -> repository.save(item)).isSameAs(exception);
    }

    @Test
    void 조회_중_DynamoDB_예외가_발생하면_그대로_전파한다() {
        RuntimeException exception = DynamoDbException.builder().message("query failed").build();
        when(table.query(any(QueryEnhancedRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> repository.findAllBySessionId("session-1"))
                .isSameAs(exception);
    }

    @Test
    void 삭제_중_DynamoDB_예외가_발생하면_그대로_전파한다() {
        TrainingEventItem item = createItem("event-1", 1_000L);
        RuntimeException exception = DynamoDbException.builder().message("delete failed").build();
        doThrow(exception).when(table).deleteItem(any(Key.class));

        assertThatThrownBy(() -> repository.delete(item)).isSameAs(exception);
    }

    @Test
    void 조회_limit은_양수여야_한다() {
        assertThatThrownBy(() -> repository.findAllBySessionId("session-1", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit은 0보다 커야합니다.");
    }

    private void mockQueryResult(
            List<List<TrainingEventItem>> pageItems
    ) {
        List<Page<TrainingEventItem>> pageList = pageItems.stream()
                .map(items -> Page.builder(TrainingEventItem.class)
                        .items(items)
                        .build())
                .toList();

        PageIterable<TrainingEventItem> result =
                PageIterable.create(pageList::iterator);

        when(table.query(any(QueryEnhancedRequest.class)))
                .thenReturn(result);
    }

    private void assertQuery(String expectedPk, String expectedSkPrefix, int expectedLimit) {
        ArgumentCaptor<QueryEnhancedRequest> captor =
                ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());

        QueryEnhancedRequest request = captor.getValue();
        assertThat(request.limit()).isEqualTo(expectedLimit);
        assertThat(request.queryConditional()
                .expression(TABLE_SCHEMA, TableMetadata.primaryIndexName())
                .expressionValues()
                .values())
                .extracting(value -> value.s())
                .contains(expectedPk, expectedSkPrefix);
    }

    private TrainingEventItem createItem(String eventId, long timestamp) {
        return TrainingEventItem.create(
                "session-1", eventId, timestamp,
                EventType.IOT_LIGHT_DIRECTION_CHANGED, "CONGESTION",
                List.of("edge-1"), List.of("node-1", "node-2"),
                List.of("node-1", "node-3"), "LIGHT-01",
                IoTLightDirection.LEFT, IoTLightDirection.RIGHT
        );
    }
}
