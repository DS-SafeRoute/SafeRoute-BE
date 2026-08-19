package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@ExtendWith(MockitoExtension.class)
class ObservationRepositoryTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDbTable<ObservationItem> table;
    @Mock
    private DynamoDbIndex<ObservationItem> gsi1;

    private ObservationRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq("saferoute-telemetry"), any(TableSchema.class))).thenReturn(table);
        when(table.index("GSI1")).thenReturn(gsi1);
        repository = new ObservationRepository(enhancedClient, "saferoute-telemetry", "GSI1");
    }

    @Test
    void eventId가_처음이면_조건부로_관측값을_저장한다() {
        ObservationItem item = item("observation-1", 1_000L);
        ArgumentCaptor<PutItemEnhancedRequest<ObservationItem>> captor = requestCaptor();

        IdempotentSaveResult<ObservationItem> result = repository.saveIfAbsent(item);

        verify(table).putItem(captor.capture());
        assertThat(captor.getValue().conditionExpression().expression())
                .isEqualTo("attribute_not_exists(#pk)");
        assertThat(result.created()).isTrue();
        assertThat(result.item()).isSameAs(item);
    }

    @Test
    void 같은_eventId면_기존_관측값을_반환한다() {
        ObservationItem incoming = item("observation-1", 1_000L);
        ObservationItem existing = item("observation-1", 900L);
        doThrow(ConditionalCheckFailedException.builder().message("duplicate").build())
                .when(table).putItem(any(PutItemEnhancedRequest.class));
        when(table.getItem(any(software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest.class)))
                .thenReturn(existing);

        IdempotentSaveResult<ObservationItem> result = repository.saveIfAbsent(incoming);

        assertThat(result.created()).isFalse();
        assertThat(result.item()).isSameAs(existing);
    }

    @Test
    void 세션과_CCTV의_관측값을_GSI에서_시간순으로_조회한다() {
        ObservationItem first = item("observation-1", 1_000L);
        ObservationItem second = item("observation-2", 2_000L);
        when(gsi1.query(any(QueryEnhancedRequest.class))).thenReturn(
                PageIterable.create(() -> List.of(Page.builder(ObservationItem.class)
                        .items(List.of(first, second)).build()).iterator())
        );
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        List<ObservationItem> result =
                repository.findAllBySessionIdAndCctvCode("session-1", "CCTV_001", 10);

        verify(gsi1).query(captor.capture());
        assertThat(captor.getValue().scanIndexForward()).isTrue();
        assertThat(captor.getValue().limit()).isEqualTo(10);
        assertThat(captor.getValue().queryConditional()
                .expression(TableSchema.fromBean(ObservationItem.class), ObservationItem.GSI1_NAME)
                .expressionValues().values())
                .extracting(value -> value.s())
                .contains("SESSION#session-1#CCTV#CCTV_001");
        assertThat(result).containsExactly(first, second);
    }

    @Test
    void 조회_limit은_양수여야_한다() {
        assertThatThrownBy(() -> repository.findAllBySessionIdAndCctvCode("session-1", "CCTV_001", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<PutItemEnhancedRequest<ObservationItem>> requestCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(PutItemEnhancedRequest.class);
    }

    private ObservationItem item(String eventId, long capturedAt) {
        return ObservationItem.create(
                UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8)), SESSION_ID,
                "CCTV_001", 5.0, 8, 25, 2.5,
                CongestionLevel.CAUTION, capturedAt - 5_000, capturedAt,
                capturedAt, null, 1L
        );
    }
}
