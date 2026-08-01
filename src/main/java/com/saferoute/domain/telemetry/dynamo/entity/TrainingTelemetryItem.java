package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class TrainingTelemetryItem {

    private String pk;
    private String sk;
    private String sessionId;
    private String cameraCode;
    private Long timestamp;
    private Integer personCount;
    private Integer capacity;
    private Double congestionRate;
    private CongestionLevel congestionLevel;
    // DynamoDB TTL 속성 (테이블 설정에서 "expiresAt"을 TTL 속성으로 지정해야 함)
    private Long expiresAt;

    public TrainingTelemetryItem() {
    }

    private TrainingTelemetryItem(String sessionId, String cameraCode, long timestamp,
                                  Integer personCount, Integer capacity, Double congestionRate,
                                  CongestionLevel congestionLevel, long expiresAt) {
        this.sessionId = sessionId;
        this.cameraCode = cameraCode;
        this.timestamp = timestamp;
        this.personCount = personCount;
        this.capacity = capacity;
        this.congestionRate = congestionRate;
        this.congestionLevel = congestionLevel;
        this.expiresAt = expiresAt;
        this.pk = buildPk(sessionId);
        this.sk = buildSk(timestamp, cameraCode);
    }

    // 텔레메트리 저장용 정적 팩토리 메서드
    public static TrainingTelemetryItem create(String sessionId, String cameraCode, long timestamp,
                                               Integer personCount, Integer capacity, Double congestionRate,
                                               CongestionLevel congestionLevel, long expiresAt) {
        return new TrainingTelemetryItem(sessionId, cameraCode, timestamp, personCount, capacity,
                congestionRate, congestionLevel, expiresAt);
    }

    public static String buildPk(String sessionId) {
        return "TRAINING#" + sessionId;
    }

    public static String buildSk(long timestamp, String cameraCode) {
        return "TELEMETRY#" + timestamp + "#" + cameraCode;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCameraCode() { return cameraCode; }
    public void setCameraCode(String cameraCode) { this.cameraCode = cameraCode; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Integer getPersonCount() { return personCount; }
    public void setPersonCount(Integer personCount) { this.personCount = personCount; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Double getCongestionRate() { return congestionRate; }
    public void setCongestionRate(Double congestionRate) { this.congestionRate = congestionRate; }

    public CongestionLevel getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(CongestionLevel congestionLevel) { this.congestionLevel = congestionLevel; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
}