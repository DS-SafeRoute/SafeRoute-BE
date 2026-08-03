package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.TrainingTelemetryItem;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;

@ExtendWith(MockitoExtension.class)
class TrainingTelemetryRepositoryTest {

    private static final String TABLE_NAME = "saferoute-telemetry";

    private static final TableSchema<TrainingTelemetryItem> TABLE_SCHEMA =
            TableSchema.fromBean(TrainingTelemetryItem.class);

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<TrainingTelemetryItem> table;

    @Mock
    private PageIterable<TrainingTelemetryItem> pages;

    @Mock
    private Page<TrainingTelemetryItem> page;

    private TrainingTelemetryRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(
                eq(TABLE_NAME),
                any(TableSchema.class)
        )).thenReturn(table);

        repository = new TrainingTelemetryRepository(
                enhancedClient,
                TABLE_NAME
        );
    }

    @Test
    void 텔레메트리를_저장한다() {
        TrainingTelemetryItem item = createItem();

        repository.save(item);

        verify(table).putItem(item);
    }

    @Test
    void 훈련_ID와_TELEMETRY_접두사로_제한된_건수만_조회한다() {
        TrainingTelemetryItem item = createItem();
        mockQueryResult(item);

        List<TrainingTelemetryItem> result =
                repository.findAllBySessionId("session-1", 50);

        assertThat(result).containsExactly(item);
        assertQuery("TRAINING#session-1", "TELEMETRY#", 50);
    }

    @Test
    void 조회_결과가_없으면_빈_목록을_반환한다() {
        when(table.query(any(QueryEnhancedRequest.class)))
                .thenReturn(pages);
        when(pages.stream()).thenReturn(Stream.empty());

        assertThat(repository.findAllBySessionId("session-1"))
                .isEmpty();
    }

    @Test
    void 텔레메트리를_삭제할_때_PK와_SK를_전달한다() {
        TrainingTelemetryItem item = createItem();
        ArgumentCaptor<Key> captor = ArgumentCaptor.forClass(Key.class);

        repository.delete(item);

        verify(table).deleteItem(captor.capture());

        assertThat(captor.getValue().partitionKeyValue().s())
                .isEqualTo(item.getPk());

        assertThat(captor.getValue().sortKeyValue())
                .hasValueSatisfying(
                        value -> assertThat(value.s())
                                .isEqualTo(item.getSk())
                );
    }

    @Test
    void 저장_중_DynamoDB_예외가_발생하면_그대로_전파한다() {
        TrainingTelemetryItem item = createItem();
        RuntimeException exception = DynamoDbException.builder()
                .message("put failed")
                .build();

        doThrow(exception).when(table).putItem(item);

        assertThatThrownBy(() -> repository.save(item))
                .isSameAs(exception);
    }

    @Test
    void 조회_중_DynamoDB_예외가_발생하면_그대로_전파한다() {
        RuntimeException exception = DynamoDbException.builder()
                .message("query failed")
                .build();

        when(table.query(any(QueryEnhancedRequest.class)))
                .thenThrow(exception);

        assertThatThrownBy(
                () -> repository.findAllBySessionId("session-1")
        ).isSameAs(exception);
    }

    @Test
    void 삭제_중_DynamoDB_예외가_발생하면_그대로_전파한다() {
        TrainingTelemetryItem item = createItem();
        RuntimeException exception = DynamoDbException.builder()
                .message("delete failed")
                .build();

        doThrow(exception).when(table).deleteItem(any(Key.class));

        assertThatThrownBy(() -> repository.delete(item))
                .isSameAs(exception);
    }

    @Test
    void 조회_limit은_양수여야_한다() {
        assertThatThrownBy(
                () -> repository.findAllBySessionId("session-1", 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be greater than zero");
    }

    private void mockQueryResult(TrainingTelemetryItem item) {
        when(table.query(any(QueryEnhancedRequest.class)))
                .thenReturn(pages);
        when(pages.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(List.of(item));
    }

    private void assertQuery(
            String expectedPk,
            String expectedSkPrefix,
            int expectedLimit
    ) {
        ArgumentCaptor<QueryEnhancedRequest> captor =
                ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        verify(table).query(captor.capture());

        QueryEnhancedRequest request = captor.getValue();

        assertThat(request.limit()).isEqualTo(expectedLimit);

        assertThat(
                request.queryConditional()
                        .expression(
                                TABLE_SCHEMA,
                                TableMetadata.primaryIndexName()
                        )
                        .expressionValues()
                        .values()
        )
                .extracting(value -> value.s())
                .contains(expectedPk, expectedSkPrefix);
    }

    private TrainingTelemetryItem createItem() {
        return TrainingTelemetryItem.create(
                "session-1",
                "camera-1",
                1_000L,
                4,
                10,
                0.4,
                CongestionLevel.LOW,
                3_600L
        );
    }
}