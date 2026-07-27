package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class CongestionSummaryItem {

    private String pk;
    private String sk;
    private String sessionId;
    private String edgeId;
    private String cctvCode;
    private Integer avgHeadcount;
    private Integer peakHeadcount;
    private CongestionLevel congestionLevel;
    private Long windowStart;
    private Long windowEnd;
    // 혼잡도 감지 시 S3에 저장된 캡처 이미지 키 (일반 5초 스냅샷엔 안 채움)
    private String s3ImageKey;

    public CongestionSummaryItem() {
    }

    private CongestionSummaryItem(String sessionId, String edgeId, String cctvCode,
                                  Integer avgHeadcount, Integer peakHeadcount, CongestionLevel congestionLevel,
                                  long windowStart, long windowEnd, String s3ImageKey) {
        this.sessionId = sessionId;
        this.edgeId = edgeId;
        this.cctvCode = cctvCode;
        this.avgHeadcount = avgHeadcount;
        this.peakHeadcount = peakHeadcount;
        this.congestionLevel = congestionLevel;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.s3ImageKey = s3ImageKey;
        this.pk = buildPk(sessionId);
        this.sk = buildSk(edgeId, windowStart);
    }

    // 혼잡도 요약 저장용 정적 팩토리 메서드 (TTL 없음 - 대시보드/리포트에서 계속 조회)
    public static CongestionSummaryItem create(String sessionId, String edgeId, String cctvCode,
                                               Integer avgHeadcount, Integer peakHeadcount, CongestionLevel congestionLevel,
                                               long windowStart, long windowEnd, String s3ImageKey) {
        return new CongestionSummaryItem(sessionId, edgeId, cctvCode, avgHeadcount, peakHeadcount,
                congestionLevel, windowStart, windowEnd, s3ImageKey);
    }

    public static String buildPk(String sessionId) {
        return "TRAINING#" + sessionId;
    }

    public static String buildSk(String edgeId, long windowStart) {
        return "CONGESTION_SUMMARY#" + edgeId + "#" + windowStart;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getEdgeId() { return edgeId; }
    public void setEdgeId(String edgeId) { this.edgeId = edgeId; }

    public String getCctvCode() { return cctvCode; }
    public void setCctvCode(String cctvCode) { this.cctvCode = cctvCode; }

    public Integer getAvgHeadcount() { return avgHeadcount; }
    public void setAvgHeadcount(Integer avgHeadcount) { this.avgHeadcount = avgHeadcount; }

    public Integer getPeakHeadcount() { return peakHeadcount; }
    public void setPeakHeadcount(Integer peakHeadcount) { this.peakHeadcount = peakHeadcount; }

    public CongestionLevel getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(CongestionLevel congestionLevel) { this.congestionLevel = congestionLevel; }

    public Long getWindowStart() { return windowStart; }
    public void setWindowStart(Long windowStart) { this.windowStart = windowStart; }

    public Long getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Long windowEnd) { this.windowEnd = windowEnd; }

    public String getS3ImageKey() { return s3ImageKey; }
    public void setS3ImageKey(String s3ImageKey) { this.s3ImageKey = s3ImageKey; }
}