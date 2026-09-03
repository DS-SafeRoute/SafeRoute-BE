package com.saferoute.domain.telemetry.dynamo.entity;

import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// 유도등+세션 단위 실시간 경로 이탈 판정 상태. 인메모리 상태를 두지 않고 매 Observation마다
// 이 아이템을 읽고 조건부로 갱신하는 방식으로, 서버 재시작/동시 요청에도 일관된 상태를 유지한다.
// lastProcessedCapturedAt은 지연 도착/과거 시각의 Observation이 최신 상태를 되돌리지 않도록 막는
// 조건부 쓰기(capturedAt > lastProcessedCapturedAt)의 기준값이다.
@DynamoDbBean
public class RouteDeviationStateItem {

    public static final long TTL_SECONDS = 30L * 24 * 60 * 60;

    private String pk;
    private String sk;
    private String trainingSessionId;
    private String lightId;
    private RouteDeviationState state;
    private Integer zeroStreak;
    private Long lastProcessedCapturedAt;
    private Long expiresAt;

    public RouteDeviationStateItem() {
    }

    public static RouteDeviationStateItem create(
            String trainingSessionId,
            UUID lightId,
            RouteDeviationState state,
            int zeroStreak,
            long lastProcessedCapturedAt
    ) {
        RouteDeviationStateItem item = new RouteDeviationStateItem();
        item.trainingSessionId = trainingSessionId;
        item.lightId = lightId.toString();
        item.state = state;
        item.zeroStreak = zeroStreak;
        item.lastProcessedCapturedAt = lastProcessedCapturedAt;
        item.expiresAt = Math.floorDiv(lastProcessedCapturedAt, 1_000L) + TTL_SECONDS;
        item.pk = buildPk(trainingSessionId, item.lightId);
        item.sk = "META";
        return item;
    }

    public static String buildPk(String trainingSessionId, String lightId) {
        return "ROUTE_DEVIATION_STATE#" + trainingSessionId + "#" + lightId;
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

    public String getTrainingSessionId() {
        return trainingSessionId;
    }

    public void setTrainingSessionId(String trainingSessionId) {
        this.trainingSessionId = trainingSessionId;
    }

    public String getLightId() {
        return lightId;
    }

    public void setLightId(String lightId) {
        this.lightId = lightId;
    }

    public RouteDeviationState getState() {
        return state;
    }

    public void setState(RouteDeviationState state) {
        this.state = state;
    }

    public Integer getZeroStreak() {
        return zeroStreak;
    }

    public void setZeroStreak(Integer zeroStreak) {
        this.zeroStreak = zeroStreak;
    }

    public Long getLastProcessedCapturedAt() {
        return lastProcessedCapturedAt;
    }

    public void setLastProcessedCapturedAt(Long lastProcessedCapturedAt) {
        this.lastProcessedCapturedAt = lastProcessedCapturedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
