package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.TrainingTelemetryItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@ExtendWith(MockitoExtension.class)
class TrainingTelemetryRepositoryTest {

    private static final String TABLE_NAME = "saferoute-telemetry";

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<TrainingTelemetryItem> table;

    @Mock
    private PageIterable<TrainingTelemetryItem> pages;

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
    void 훈련_ID로_텔레메트리_목록을_조회한다() {
        TrainingTelemetryItem item = createItem();
        SdkIterable<TrainingTelemetryItem> items =
                () -> List.of(item).iterator();

        when(table.query(any(QueryConditional.class)))
                .thenReturn(pages);
        when(pages.items()).thenReturn(items);

        List<TrainingTelemetryItem> result =
                repository.findAllBySessionId("session-1");

        assertThat(result).containsExactly(item);
        verify(table).query(any(QueryConditional.class));
    }

    @Test
    void 텔레메트리를_삭제한다() {
        TrainingTelemetryItem item = createItem();

        repository.delete(item);

        verify(table).deleteItem(any(Key.class));
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