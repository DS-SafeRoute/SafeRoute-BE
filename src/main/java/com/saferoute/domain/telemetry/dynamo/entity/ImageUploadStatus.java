package com.saferoute.domain.telemetry.dynamo.entity;

public enum ImageUploadStatus {
    PENDING,
    COMPLETED,
    FAILED,

    /** 기존 DynamoDB 항목 역직렬화를 위한 호환 값. 신규 저장에는 사용하지 않는다. */
    @Deprecated
    UPLOADED
}
