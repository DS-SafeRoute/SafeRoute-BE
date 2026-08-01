package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.device.entity.IoTLightDirection;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class TrainingEventItem {

    private String pk;
    private String sk;
    private String sessionId;
    private String eventId;
    private Long timestamp;
    private EventType eventType;
    private String triggerType;
    private List<String> affectedEdgeIds;
    private List<String> previousPath;
    private List<String> newPath;
    private String iotLightCode;
    private IoTLightDirection previousDirection;
    private IoTLightDirection newDirection;

    public TrainingEventItem() {
    }

    private TrainingEventItem(String sessionId, String eventId, long timestamp, EventType eventType,
                              String triggerType, List<String> affectedEdgeIds, List<String> previousPath,
                              List<String> newPath, String iotLightCode, IoTLightDirection previousDirection,
                              IoTLightDirection newDirection) {
        this.sessionId = sessionId;
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.triggerType = triggerType;
        this.affectedEdgeIds = affectedEdgeIds;
        this.previousPath = previousPath;
        this.newPath = newPath;
        this.iotLightCode = iotLightCode;
        this.previousDirection = previousDirection;
        this.newDirection = newDirection;
        this.pk = buildPk(sessionId);
        this.sk = buildSk(timestamp, eventId);
    }

    // 이벤트 로그 저장용 정적 팩토리 메서드 (TTL 없음)
    public static TrainingEventItem create(String sessionId, String eventId, long timestamp,
                                           EventType eventType, String triggerType, List<String> affectedEdgeIds,
                                           List<String> previousPath, List<String> newPath, String iotLightCode,
                                           IoTLightDirection previousDirection, IoTLightDirection newDirection) {
        return new TrainingEventItem(sessionId, eventId, timestamp, eventType, triggerType,
                affectedEdgeIds, previousPath, newPath, iotLightCode, previousDirection, newDirection);
    }

    public static String buildPk(String sessionId) {
        return "TRAINING#" + sessionId;
    }

    public static String buildSk(long timestamp, String eventId) {
        return "EVENT#" + timestamp + "#" + eventId;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public List<String> getAffectedEdgeIds() { return affectedEdgeIds; }
    public void setAffectedEdgeIds(List<String> affectedEdgeIds) { this.affectedEdgeIds = affectedEdgeIds; }

    public List<String> getPreviousPath() { return previousPath; }
    public void setPreviousPath(List<String> previousPath) { this.previousPath = previousPath; }

    public List<String> getNewPath() { return newPath; }
    public void setNewPath(List<String> newPath) { this.newPath = newPath; }

    public String getIotLightCode() { return iotLightCode; }
    public void setIotLightCode(String iotLightCode) { this.iotLightCode = iotLightCode; }

    public IoTLightDirection getPreviousDirection() { return previousDirection; }
    public void setPreviousDirection(IoTLightDirection previousDirection) { this.previousDirection = previousDirection; }

    public IoTLightDirection getNewDirection() { return newDirection; }
    public void setNewDirection(IoTLightDirection newDirection) { this.newDirection = newDirection; }
}