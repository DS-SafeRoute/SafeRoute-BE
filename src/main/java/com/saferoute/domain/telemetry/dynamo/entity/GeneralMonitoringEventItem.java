package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// 혼잡 이벤트(CongestionEventItem)와 별도로 BE가 직접 판정해서 만드는 일반 모니터링 이벤트
// (AI_ANALYSIS_STARTED, ROUTE_DEVIATION_DETECTED)를 저장한다. eventId를 결정적으로 만들어
// attribute_not_exists 조건부 put에 태우면, 별도의 마커 아이템 없이도 세션+CCTV당 정확히
// 한 번만 저장되도록 보장할 수 있다 (예: AI 분석 시작 판정).
@DynamoDbBean
public class GeneralMonitoringEventItem {

    public static final String GSI1_NAME = "GSI1";
    public static final long TTL_SECONDS = 30L * 24 * 60 * 60;

    private String pk;
    private String sk;
    private String gsi1Pk;
    private String gsi1Sk;
    private String eventId;
    private String trainingSessionId;
    private String cctvCode;
    private GeneralMonitoringEventType eventType;
    private Long occurredAt;
    private CongestionLevel congestionLevel;
    private Long expiresAt;

    public GeneralMonitoringEventItem() {
    }

    public static GeneralMonitoringEventItem create(
            String eventId,
            String trainingSessionId,
            String cctvCode,
            GeneralMonitoringEventType eventType,
            long occurredAt,
            CongestionLevel congestionLevel
    ) {
        GeneralMonitoringEventItem item = new GeneralMonitoringEventItem();
        item.eventId = eventId;
        item.trainingSessionId = trainingSessionId;
        item.cctvCode = cctvCode;
        item.eventType = eventType;
        item.occurredAt = occurredAt;
        item.congestionLevel = congestionLevel;
        item.expiresAt = Math.floorDiv(occurredAt, 1_000L) + TTL_SECONDS;
        item.pk = buildPk(eventId);
        item.sk = "META";
        item.gsi1Pk = buildGsi1Pk(trainingSessionId);
        item.gsi1Sk = buildGsi1Sk(occurredAt, eventId);
        return item;
    }

    public static String buildPk(String eventId) {
        return "MONITORING_EVENT#" + eventId;
    }

    public static String buildGsi1Pk(String trainingSessionId) {
        return "SESSION#" + trainingSessionId;
    }

    public static String buildGsi1Sk(long occurredAt, String eventId) {
        return "EVENT#" + occurredAt + "#" + eventId;
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

    @DynamoDbAttribute("GSI1_PK")
    @DynamoDbSecondaryPartitionKey(indexNames = GSI1_NAME)
    public String getGsi1Pk() {
        return gsi1Pk;
    }

    public void setGsi1Pk(String gsi1Pk) {
        this.gsi1Pk = gsi1Pk;
    }

    @DynamoDbAttribute("GSI1_SK")
    @DynamoDbSecondarySortKey(indexNames = GSI1_NAME)
    public String getGsi1Sk() {
        return gsi1Sk;
    }

    public void setGsi1Sk(String gsi1Sk) {
        this.gsi1Sk = gsi1Sk;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTrainingSessionId() {
        return trainingSessionId;
    }

    public void setTrainingSessionId(String trainingSessionId) {
        this.trainingSessionId = trainingSessionId;
    }

    public String getCctvCode() {
        return cctvCode;
    }

    public void setCctvCode(String cctvCode) {
        this.cctvCode = cctvCode;
    }

    public GeneralMonitoringEventType getEventType() {
        return eventType;
    }

    public void setEventType(GeneralMonitoringEventType eventType) {
        this.eventType = eventType;
    }

    public Long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Long occurredAt) {
        this.occurredAt = occurredAt;
    }

    public CongestionLevel getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(CongestionLevel congestionLevel) {
        this.congestionLevel = congestionLevel;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
