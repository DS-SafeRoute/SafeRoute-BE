package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class CongestionEventItem {

    public static final String GSI1_NAME = "GSI1";

    private String pk;
    private String sk;
    private String gsi1Pk;
    private String gsi1Sk;
    private String eventId;
    private String trainingSessionId;
    private String cctvCode;
    private CongestionEventType eventType;
    private Long detectedAt;
    private Integer headcount;
    private Double localDensity;
    private CongestionLevel localCongestionLevel;
    private Double density;
    private CongestionLevel congestionLevel;
    private Long configVersion;
    private String eventImageKey;
    private EventProcessingStatus eventStatus;
    private ImageUploadStatus imageUploadStatus;

    public CongestionEventItem() {
    }

    public static CongestionEventItem received(
            UUID eventId,
            UUID trainingSessionId,
            String cctvCode,
            CongestionEventType eventType,
            long detectedAt,
            Integer headcount,
            Double localDensity,
            CongestionLevel localCongestionLevel,
            Double density,
            CongestionLevel congestionLevel,
            long configVersion,
            String eventImageKey
    ) {
        CongestionEventItem item = new CongestionEventItem();
        item.eventId = eventId.toString();
        item.trainingSessionId = trainingSessionId.toString();
        item.cctvCode = cctvCode;
        item.eventType = eventType;
        item.detectedAt = detectedAt;
        item.headcount = headcount;
        item.localDensity = localDensity;
        item.localCongestionLevel = localCongestionLevel;
        item.density = density;
        item.congestionLevel = congestionLevel;
        item.configVersion = configVersion;
        item.eventImageKey = eventImageKey;
        item.eventStatus = EventProcessingStatus.RECEIVED;
        item.imageUploadStatus = ImageUploadStatus.PENDING;
        item.pk = buildPk(item.eventId);
        item.sk = "META";
        item.gsi1Pk = buildGsi1Pk(item.trainingSessionId);
        item.gsi1Sk = buildGsi1Sk(detectedAt, item.eventId);
        return item;
    }

    public static String buildPk(String eventId) {
        return "CONGESTION_EVENT#" + eventId;
    }

    public static String buildGsi1Pk(String trainingSessionId) {
        return "SESSION#" + trainingSessionId;
    }

    public static String buildGsi1Sk(long detectedAt, String eventId) {
        return "EVENT#" + detectedAt + "#" + eventId;
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

    public CongestionEventType getEventType() {
        return eventType;
    }

    public void setEventType(CongestionEventType eventType) {
        this.eventType = eventType;
    }

    public Long getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Long detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Integer getHeadcount() {
        return headcount;
    }

    public void setHeadcount(Integer headcount) {
        this.headcount = headcount;
    }

    public Double getLocalDensity() {
        return localDensity;
    }

    public void setLocalDensity(Double localDensity) {
        this.localDensity = localDensity;
    }

    public CongestionLevel getLocalCongestionLevel() {
        return localCongestionLevel;
    }

    public void setLocalCongestionLevel(CongestionLevel localCongestionLevel) {
        this.localCongestionLevel = localCongestionLevel;
    }

    public Double getDensity() {
        return density;
    }

    public void setDensity(Double density) {
        this.density = density;
    }

    public CongestionLevel getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(CongestionLevel congestionLevel) {
        this.congestionLevel = congestionLevel;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public String getEventImageKey() {
        return eventImageKey;
    }

    public void setEventImageKey(String eventImageKey) {
        this.eventImageKey = eventImageKey;
    }

    public EventProcessingStatus getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(EventProcessingStatus eventStatus) {
        this.eventStatus = eventStatus;
    }

    public ImageUploadStatus getImageUploadStatus() {
        return imageUploadStatus;
    }

    public void setImageUploadStatus(ImageUploadStatus imageUploadStatus) {
        this.imageUploadStatus = imageUploadStatus;
    }
}
