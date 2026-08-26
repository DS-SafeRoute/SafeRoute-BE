package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.telemetry.dynamo.entity.LightDirectionEventItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@ExtendWith(MockitoExtension.class)
class LightDirectionEventRepositoryTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LIGHT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDbTable<LightDirectionEventItem> table;

    private LightDirectionEventRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq("saferoute-telemetry"), any(TableSchema.class))).thenReturn(table);
        repository = new LightDirectionEventRepository(enhancedClient, "saferoute-telemetry");
    }

    @Test
    void 방향_전환_이력을_저장한다() {
        LightDirectionEventItem item = item(IoTLightDirection.LEFT, 1_000L);

        repository.save(item);

        verify(table).putItem(item);
    }

    @Test
    void 세션과_유도등의_방향_전환_이력을_시간순으로_조회한다() {
        LightDirectionEventItem first = item(IoTLightDirection.LEFT, 1_000L);
        LightDirectionEventItem second = item(IoTLightDirection.RIGHT, 2_000L);
        when(table.query(any(QueryEnhancedRequest.class))).thenReturn(
                PageIterable.create(() -> List.of(Page.builder(LightDirectionEventItem.class)
                        .items(List.of(first, second)).build()).iterator())
        );
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        List<LightDirectionEventItem> result =
                repository.findAllBySessionIdAndLightCode(SESSION_ID.toString(), "LIGHT_001");

        verify(table).query(captor.capture());
        assertThat(captor.getValue().scanIndexForward()).isTrue();
        assertThat(captor.getValue().queryConditional()
                .expression(TableSchema.fromBean(LightDirectionEventItem.class),
                        software.amazon.awssdk.enhanced.dynamodb.TableMetadata.primaryIndexName())
                .expressionValues().values())
                .extracting(value -> value.s())
                .contains("LIGHT_DIRECTION#" + SESSION_ID + "#LIGHT_001");
        assertThat(result).containsExactly(first, second);
    }

    private LightDirectionEventItem item(IoTLightDirection direction, long changedAt) {
        return LightDirectionEventItem.create(
                SESSION_ID, LIGHT_ID, "LIGHT_001", direction,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), changedAt
        );
    }
}
