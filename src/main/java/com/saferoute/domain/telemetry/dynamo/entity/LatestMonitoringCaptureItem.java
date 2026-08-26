package com.saferoute.domain.telemetry.dynamo.entity;

import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class LatestMonitoringCaptureItem {

    private String pk;
    private String sk;
    private String trainingSessionId;
    private String cctvCode;
    private Long capturedAt;
    private String monitoringImageKey;
    private Long expiresAt;

    public LatestMonitoringCaptureItem() {
    }

    public static LatestMonitoringCaptureItem create(
            UUID trainingSessionId,
            String cctvCode,
            long capturedAt,
            String monitoringImageKey
    ) {
        LatestMonitoringCaptureItem item = new LatestMonitoringCaptureItem();
        item.trainingSessionId = trainingSessionId.toString();
        item.cctvCode = cctvCode;
        item.capturedAt = capturedAt;
        item.monitoringImageKey = monitoringImageKey;
        item.expiresAt = Math.floorDiv(capturedAt, 1_000L) + ObservationItem.TTL_SECONDS;
        item.pk = buildPk(item.trainingSessionId);
        item.sk = buildSk(cctvCode);
        return item;
    }

    public static String buildPk(String trainingSessionId) {
        return "LATEST_MONITORING#" + trainingSessionId;
    }

    public static String buildSk(String cctvCode) {
        return "CCTV#" + cctvCode;
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

    public String getCctvCode() {
        return cctvCode;
    }

    public void setCctvCode(String cctvCode) {
        this.cctvCode = cctvCode;
    }

    public Long getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Long capturedAt) {
        this.capturedAt = capturedAt;
    }

    public String getMonitoringImageKey() {
        return monitoringImageKey;
    }

    public void setMonitoringImageKey(String monitoringImageKey) {
        this.monitoringImageKey = monitoringImageKey;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
