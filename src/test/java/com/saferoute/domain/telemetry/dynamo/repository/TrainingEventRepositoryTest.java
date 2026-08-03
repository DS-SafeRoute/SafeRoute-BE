package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.telemetry.dynamo.entity.EventType;
import com.saferoute.domain.telemetry.dynamo.entity.TrainingEventItem;
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
class TrainingEventRepositoryTest {

    private static final String TABLE_NAME = "saferoute-telemetry";

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<TrainingEventItem> table;

    @Mock
    private PageIterable<TrainingEventItem> pages;

    private TrainingEventRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(
                eq(TABLE_NAME),
                any(TableSchema.class)
        )).thenReturn(table);

        repository = new TrainingEventRepository(
                enhancedClient,
                TABLE_NAME
        );
    }

    @Test
    void 훈련_이벤트를_저장한다() {
        TrainingEventItem item = createItem();

        repository.save(item);

        verify(table).putItem(item);
    }

    @Test
    void 훈련_ID로_이벤트_목록을_조회한다() {
        TrainingEventItem item = createItem();
        SdkIterable<TrainingEventItem> items =
                () -> List.of(item).iterator();

        when(table.query(any(QueryConditional.class)))
                .thenReturn(pages);
        when(pages.items()).thenReturn(items);

        List<TrainingEventItem> result =
                repository.findAllBySessionId("session-1");

        assertThat(result).containsExactly(item);
        verify(table).query(any(QueryConditional.class));
    }

    @Test
    void 훈련_이벤트를_삭제한다() {
        TrainingEventItem item = createItem();

        repository.delete(item);

        verify(table).deleteItem(any(Key.class));
    }

    private TrainingEventItem createItem() {
        return TrainingEventItem.create(
                "session-1",
                "event-1",
                1_000L,
                EventType.IOT_LIGHT_DIRECTION_CHANGED,
                "CONGESTION",
                List.of("edge-1"),
                List.of("node-1", "node-2"),
                List.of("node-1", "node-3"),
                "LIGHT-01",
                IoTLightDirection.LEFT,
                IoTLightDirection.RIGHT
        );
    }
}