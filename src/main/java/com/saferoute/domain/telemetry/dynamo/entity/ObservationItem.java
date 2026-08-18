package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class ObservationItem {

    public static final String GSI1_NAME = "GSI1";
    public static final long TTL_SECONDS = 30L * 24 * 60 * 60;

    private String pk;
    private String sk;
    private String gsi1Pk;
    private String gsi1Sk;
    private String eventId;
    private String trainingSessionId;
    private String cctvCode;
    private Double avgHeadcount;
    private Integer peakHeadcount;
    private Integer sampleCount;
    private Double density;
    private CongestionLevel congestionLevel;
    private Long windowStart;
    private Long windowEnd;
    private Long capturedAt;
    private String monitoringImageKey;
    private Long configVersion;
    private Long expiresAt;

    public ObservationItem() {
    }

    public static ObservationItem create(
            String eventId,
            String trainingSessionId,
            String cctvCode,
            Double avgHeadcount,
            Integer peakHeadcount,
            Integer sampleCount,
            Double density,
            CongestionLevel congestionLevel,
            long windowStart,
            long windowEnd,
            long capturedAt,
            String monitoringImageKey,
            long configVersion
    ) {
        ObservationItem item = new ObservationItem();
        item.eventId = eventId;
        item.trainingSessionId = trainingSessionId;
        item.cctvCode = cctvCode;
        item.avgHeadcount = avgHeadcount;
        item.peakHeadcount = peakHeadcount;
        item.sampleCount = sampleCount;
        item.density = density;
        item.congestionLevel = congestionLevel;
        item.windowStart = windowStart;
        item.windowEnd = windowEnd;
        item.capturedAt = capturedAt;
        item.monitoringImageKey = monitoringImageKey;
        item.configVersion = configVersion;
        item.expiresAt = Math.floorDiv(capturedAt, 1_000L) + TTL_SECONDS;
        item.pk = buildPk(eventId);
        item.sk = "META";
        item.gsi1Pk = buildGsi1Pk(trainingSessionId, cctvCode);
        item.gsi1Sk = buildGsi1Sk(capturedAt);
        return item;
    }

    public static String buildPk(String eventId) {
        return "OBSERVATION#" + eventId;
    }

    public static String buildGsi1Pk(String trainingSessionId, String cctvCode) {
        return "SESSION#" + trainingSessionId + "#CCTV#" + cctvCode;
    }

    public static String buildGsi1Sk(long capturedAt) {
        return "TIME#" + capturedAt;
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

    public Double getAvgHeadcount() {
        return avgHeadcount;
    }

    public void setAvgHeadcount(Double avgHeadcount) {
        this.avgHeadcount = avgHeadcount;
    }

    public Integer getPeakHeadcount() {
        return peakHeadcount;
    }

    public void setPeakHeadcount(Integer peakHeadcount) {
        this.peakHeadcount = peakHeadcount;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
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

    public Long getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Long windowStart) {
        this.windowStart = windowStart;
    }

    public Long getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Long windowEnd) {
        this.windowEnd = windowEnd;
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

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
