package com.saferoute.domain.telemetry.dynamo.entity;

import com.saferoute.domain.device.entity.IoTLightDirection;
import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// 유도등의 방향 전환 이력을 훈련 세션 단위로 기록한다. 실시간 현재 방향은 IoTLightDirectionStore(서버 메모리)가
// 담당하고, 이 아이템은 "언제 어느 방향으로 바뀌었는지"의 시계열 이력만 담아 리포트/경로 이탈률 계산에 쓴다.
@DynamoDbBean
public class LightDirectionEventItem {

    public static final long TTL_SECONDS = 30L * 24 * 60 * 60;

    private String pk;
    private String sk;
    private String trainingSessionId;
    private String lightId;
    private String lightCode;
    private IoTLightDirection direction;
    private String decisionNodeId;
    private String leftEdgeId;
    private String rightEdgeId;
    private Long changedAt;
    private Long expiresAt;

    public LightDirectionEventItem() {
    }

    public static LightDirectionEventItem create(
            UUID trainingSessionId,
            UUID lightId,
            String lightCode,
            IoTLightDirection direction,
            UUID decisionNodeId,
            UUID leftEdgeId,
            UUID rightEdgeId,
            long changedAt
    ) {
        LightDirectionEventItem item = new LightDirectionEventItem();
        item.trainingSessionId = trainingSessionId.toString();
        item.lightId = lightId.toString();
        item.lightCode = lightCode;
        item.direction = direction;
        item.decisionNodeId = decisionNodeId != null ? decisionNodeId.toString() : null;
        item.leftEdgeId = leftEdgeId != null ? leftEdgeId.toString() : null;
        item.rightEdgeId = rightEdgeId != null ? rightEdgeId.toString() : null;
        item.changedAt = changedAt;
        item.expiresAt = Math.floorDiv(changedAt, 1_000L) + TTL_SECONDS;
        item.pk = buildPk(item.trainingSessionId, lightCode);
        item.sk = buildSk(changedAt, UUID.randomUUID().toString());
        return item;
    }

    public static String buildPk(String trainingSessionId, String lightCode) {
        return "LIGHT_DIRECTION#" + trainingSessionId + "#" + lightCode;
    }

    // changedAt만으로는 같은 밀리초에 발생한 두 전환이 같은 정렬 키를 갖게 되어 뒤의 저장이 앞의 이력을
    // 덮어쓸 수 있다. 시간순 정렬은 changedAt 접두사로 유지하면서, UUID를 tie-breaker로 덧붙여 유일성을 보장한다.
    public static String buildSk(long changedAt, String tieBreaker) {
        return "TIME#" + changedAt + "#" + tieBreaker;
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

    public String getLightCode() {
        return lightCode;
    }

    public void setLightCode(String lightCode) {
        this.lightCode = lightCode;
    }

    public IoTLightDirection getDirection() {
        return direction;
    }

    public void setDirection(IoTLightDirection direction) {
        this.direction = direction;
    }

    public String getDecisionNodeId() {
        return decisionNodeId;
    }

    public void setDecisionNodeId(String decisionNodeId) {
        this.decisionNodeId = decisionNodeId;
    }

    public String getLeftEdgeId() {
        return leftEdgeId;
    }

    public void setLeftEdgeId(String leftEdgeId) {
        this.leftEdgeId = leftEdgeId;
    }

    public String getRightEdgeId() {
        return rightEdgeId;
    }

    public void setRightEdgeId(String rightEdgeId) {
        this.rightEdgeId = rightEdgeId;
    }

    public Long getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Long changedAt) {
        this.changedAt = changedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
