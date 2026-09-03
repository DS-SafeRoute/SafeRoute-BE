package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventType;
import java.util.List;
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
class GeneralMonitoringEventRepositoryTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDbTable<GeneralMonitoringEventItem> table;
    @Mock
    private DynamoDbIndex<GeneralMonitoringEventItem> gsi1;

    private GeneralMonitoringEventRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq("saferoute-telemetry"), any(TableSchema.class))).thenReturn(table);
        when(table.index("GSI1")).thenReturn(gsi1);
        repository = new GeneralMonitoringEventRepository(enhancedClient, "saferoute-telemetry", "GSI1");
    }

    @Test
    void eventId가_처음이면_조건부로_저장한다() {
        GeneralMonitoringEventItem item = item("ai-analysis-started:session:CCTV_001", 1_000L);
        ArgumentCaptor<PutItemEnhancedRequest<GeneralMonitoringEventItem>> captor = requestCaptor();

        IdempotentSaveResult<GeneralMonitoringEventItem> result = repository.saveIfAbsent(item);

        org.mockito.Mockito.verify(table).putItem(captor.capture());
        assertThat(captor.getValue().conditionExpression().expression())
                .isEqualTo("attribute_not_exists(#pk)");
        assertThat(result.created()).isTrue();
    }

    @Test
    void 같은_eventId면_기존_이벤트를_반환하고_덮어쓰지_않는다() {
        GeneralMonitoringEventItem incoming = item("ai-analysis-started:session:CCTV_001", 2_000L);
        GeneralMonitoringEventItem existing = item("ai-analysis-started:session:CCTV_001", 1_000L);
        doThrow(ConditionalCheckFailedException.builder().message("duplicate").build())
                .when(table).putItem(any(PutItemEnhancedRequest.class));
        when(table.getItem(any(software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest.class)))
                .thenReturn(existing);

        IdempotentSaveResult<GeneralMonitoringEventItem> result = repository.saveIfAbsent(incoming);

        assertThat(result.created()).isFalse();
        assertThat(result.item()).isSameAs(existing);
    }

    @Test
    void 이벤트_타임라인은_최신순으로_조회한다() {
        GeneralMonitoringEventItem newest = item("event-1", 2_000L);
        GeneralMonitoringEventItem oldest = item("event-2", 1_000L);
        when(gsi1.query(any(QueryEnhancedRequest.class))).thenReturn(
                PageIterable.create(() -> List.of(Page.builder(GeneralMonitoringEventItem.class)
                        .items(List.of(newest, oldest)).build()).iterator())
        );
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        List<GeneralMonitoringEventItem> result = repository.findPageBySessionId(
                SESSION_ID.toString(), null, 10, null, null);

        org.mockito.Mockito.verify(gsi1).query(captor.capture());
        assertThat(captor.getValue().scanIndexForward()).isFalse();
        assertThat(captor.getValue().limit()).isEqualTo(10);
        assertThat(captor.getValue().filterExpression()).isNull();
        assertThat(result).containsExactly(newest, oldest);
    }

    @Test
    void cctvCode가_있으면_필터_익스프레션을_적용한다() {
        when(gsi1.query(any(QueryEnhancedRequest.class))).thenReturn(
                PageIterable.create(() -> List.of(Page.builder(GeneralMonitoringEventItem.class)
                        .items(List.of()).build()).iterator())
        );
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        repository.findPageBySessionId(SESSION_ID.toString(), "CCTV_001", 10, null, null);

        org.mockito.Mockito.verify(gsi1).query(captor.capture());
        assertThat(captor.getValue().filterExpression()).isNotNull();
        assertThat(captor.getValue().filterExpression().expression())
                .isEqualTo("#cctvCode = :cctvCode");
        assertThat(captor.getValue().filterExpression().expressionValues().get(":cctvCode").s())
                .isEqualTo("CCTV_001");
    }

    @Test
    void cursor가_있으면_해당_시각_이전_이벤트만_조회한다() {
        when(gsi1.query(any(QueryEnhancedRequest.class))).thenReturn(
                PageIterable.create(() -> List.of(Page.builder(GeneralMonitoringEventItem.class)
                        .items(List.of()).build()).iterator())
        );
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);

        repository.findPageBySessionId(SESSION_ID.toString(), null, 10, 2_000L, "event-1");

        org.mockito.Mockito.verify(gsi1).query(captor.capture());
        assertThat(captor.getValue().queryConditional()
                .expression(TableSchema.fromBean(GeneralMonitoringEventItem.class), GeneralMonitoringEventItem.GSI1_NAME)
                .expressionValues().values())
                .extracting(value -> value.s())
                .contains("EVENT#0000000000000002000#event-1");
    }

    @Test
    void limit이_0이하면_거부한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> repository.findPageBySessionId(SESSION_ID.toString(), null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<PutItemEnhancedRequest<GeneralMonitoringEventItem>> requestCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(PutItemEnhancedRequest.class);
    }

    private GeneralMonitoringEventItem item(String eventId, long occurredAt) {
        return GeneralMonitoringEventItem.create(
                eventId, SESSION_ID.toString(), "CCTV_001",
                GeneralMonitoringEventType.AI_ANALYSIS_STARTED, occurredAt, null
        );
    }
}
