package com.saferoute.domain.telemetry.dynamo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.domain.telemetry.dynamo.entity.ObservationCountItem;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@ExtendWith(MockitoExtension.class)
class ObservationCountRepositoryTest {

    private static final String TABLE_NAME = "saferoute-telemetry";
    private static final String GSI1_NAME = "GSI1";

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDbTable<ObservationCountItem> table;

    private ObservationCountRepository repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(table);
        repository = new ObservationCountRepository(dynamoDbClient, enhancedClient, TABLE_NAME, GSI1_NAME);
    }

    @Test
    void increment은_ADD_원자_연산으로_카운터를_1_증가시킨다() {
        repository.increment("session-1", "CCTV_001");

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();

        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo("OBSERVATION_COUNT#session-1#CCTV_001");
        assertThat(request.key().get("sk").s()).isEqualTo("META");
        assertThat(request.updateExpression()).isEqualTo("ADD #count :incr");
        assertThat(request.expressionAttributeValues().get(":incr").n()).isEqualTo("1");
    }

    @Test
    void find은_카운터_아이템이_있으면_count를_반환한다() {
        ObservationCountItem item = new ObservationCountItem();
        item.setCount(137L);
        given(table.getItem(any(GetItemEnhancedRequest.class))).willReturn(item);

        Optional<Long> result = repository.find("session-1", "CCTV_001");

        assertThat(result).contains(137L);
    }

    @Test
    void find은_카운터_아이템이_없으면_빈_값을_반환한다() {
        given(table.getItem(any(GetItemEnhancedRequest.class))).willReturn(null);

        Optional<Long> result = repository.find("session-1", "CCTV_001");

        assertThat(result).isEmpty();
    }

    @Test
    void countAllBySessionIdAndCctvCode는_Select_COUNT로_페이지를_순회해_합산한다() {
        QueryResponse firstPage = QueryResponse.builder()
                .count(100)
                .lastEvaluatedKey(Map.of("pk", AttributeValue.fromS("OBSERVATION#last-of-first-page")))
                .build();
        QueryResponse secondPage = QueryResponse.builder()
                .count(37)
                .lastEvaluatedKey(Map.of())
                .build();
        given(dynamoDbClient.query(any(QueryRequest.class))).willReturn(firstPage, secondPage);

        long total = repository.countAllBySessionIdAndCctvCode("session-1", "CCTV_001");

        assertThat(total).isEqualTo(137L);
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient, org.mockito.Mockito.times(2)).query(captor.capture());
        QueryRequest firstRequest = captor.getAllValues().get(0);
        assertThat(firstRequest.tableName()).isEqualTo(TABLE_NAME);
        assertThat(firstRequest.indexName()).isEqualTo(GSI1_NAME);
        assertThat(firstRequest.select()).isEqualTo(Select.COUNT);
        assertThat(firstRequest.exclusiveStartKey()).isNullOrEmpty();
        QueryRequest secondRequest = captor.getAllValues().get(1);
        assertThat(secondRequest.exclusiveStartKey())
                .containsEntry("pk", AttributeValue.fromS("OBSERVATION#last-of-first-page"));
    }
}
