package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionSummaryItem;
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
class CongestionSummaryRepositoryTest {

    private static final String TABLE_NAME = "saferoute-telemetry";

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<CongestionSummaryItem> table;

    @Mock
    private PageIterable<CongestionSummaryItem> pages;

    private CongestionSummaryRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(
                eq(TABLE_NAME),
                any(TableSchema.class)
        )).thenReturn(table);

        repository = new CongestionSummaryRepository(
                enhancedClient,
                TABLE_NAME
        );
    }

    @Test
    void 혼잡_요약을_저장한다() {
        CongestionSummaryItem item = createItem();

        repository.save(item);

        verify(table).putItem(item);
    }

    @Test
    void 훈련_ID로_혼잡_요약_목록을_조회한다() {
        CongestionSummaryItem item = createItem();
        mockQueryResult(item);

        List<CongestionSummaryItem> result =
                repository.findAllBySessionId("session-1");

        assertThat(result).containsExactly(item);
        verify(table).query(any(QueryConditional.class));
    }

    @Test
    void 훈련_ID와_엣지_ID로_혼잡_요약_목록을_조회한다() {
        CongestionSummaryItem item = createItem();
        mockQueryResult(item);

        List<CongestionSummaryItem> result =
                repository.findAllBySessionIdAndEdgeId(
                        "session-1",
                        "edge-1"
                );

        assertThat(result).containsExactly(item);
        verify(table).query(any(QueryConditional.class));
    }

    @Test
    void 혼잡_요약을_삭제한다() {
        CongestionSummaryItem item = createItem();

        repository.delete(item);

        verify(table).deleteItem(any(Key.class));
    }

    private void mockQueryResult(CongestionSummaryItem item) {
        SdkIterable<CongestionSummaryItem> items =
                () -> List.of(item).iterator();

        when(table.query(any(QueryConditional.class)))
                .thenReturn(pages);
        when(pages.items()).thenReturn(items);
    }

    private CongestionSummaryItem createItem() {
        return CongestionSummaryItem.create(
                "session-1",
                "edge-1",
                "CCTV-01",
                4,
                6,
                CongestionLevel.HIGH,
                1_000L,
                1_005L,
                "training/session-1/CCTV-01/1000.jpg"
        );
    }
}