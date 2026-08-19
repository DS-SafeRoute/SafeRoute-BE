package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class CurrentCctvStateItem {

    private String pk;
    private String sk;
    private String trainingSessionId;
    private String cctvCode;
    private Integer headcount;
    private Double density;
    private CongestionLevel congestionLevel;
    private Long lastDetectedAt;
    private Long configVersion;

    public CurrentCctvStateItem() {
    }

    public static CurrentCctvStateItem create(
            UUID trainingSessionId,
            String cctvCode,
            Integer headcount,
            Double density,
            CongestionLevel congestionLevel,
            long lastDetectedAt,
            long configVersion
    ) {
        CurrentCctvStateItem item = new CurrentCctvStateItem();
        item.trainingSessionId = trainingSessionId.toString();
        item.cctvCode = cctvCode;
        item.headcount = headcount;
        item.density = density;
        item.congestionLevel = congestionLevel;
        item.lastDetectedAt = lastDetectedAt;
        item.configVersion = configVersion;
        item.pk = buildPk(item.trainingSessionId);
        item.sk = buildSk(cctvCode);
        return item;
    }

    public static String buildPk(String trainingSessionId) {
        return "CURRENT_STATE#" + trainingSessionId;
    }

    public static String buildSk(String cctvCode) {
        return "CCTV#" + cctvCode;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getTrainingSessionId() { return trainingSessionId; }
    public void setTrainingSessionId(String trainingSessionId) { this.trainingSessionId = trainingSessionId; }

    public String getCctvCode() { return cctvCode; }
    public void setCctvCode(String cctvCode) { this.cctvCode = cctvCode; }

    public Integer getHeadcount() { return headcount; }
    public void setHeadcount(Integer headcount) { this.headcount = headcount; }

    public Double getDensity() { return density; }
    public void setDensity(Double density) { this.density = density; }

    public CongestionLevel getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(CongestionLevel congestionLevel) { this.congestionLevel = congestionLevel; }

    public Long getLastDetectedAt() { return lastDetectedAt; }
    public void setLastDetectedAt(Long lastDetectedAt) { this.lastDetectedAt = lastDetectedAt; }

    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
}
