package com.saferoute.domain.telemetry.dynamo.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// 세션+CCTV별로 저장된 Observation 총 개수를 원자적 ADD로 관리하는 카운터 아이템.
// 세션이 진행 중인 동안 계속 조회되는 값이라 TTL을 걸지 않는다(30일 TTL을 걸면 세션이
// 길어질 경우 카운터가 Observation보다 먼저 만료될 수 있음).
@DynamoDbBean
public class ObservationCountItem {

    private String pk;
    private String sk;
    private Long count;

    public ObservationCountItem() {
    }

    public static String buildPk(String trainingSessionId, String cctvCode) {
        return "OBSERVATION_COUNT#" + trainingSessionId + "#" + cctvCode;
    }

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
