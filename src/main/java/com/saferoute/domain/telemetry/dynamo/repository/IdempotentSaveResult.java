package com.saferoute.domain.telemetry.dynamo.repository;

public record IdempotentSaveResult<T>(T item, boolean created) {

    public static <T> IdempotentSaveResult<T> created(T item) {
        return new IdempotentSaveResult<>(item, true);
    }

    public static <T> IdempotentSaveResult<T> existing(T item) {
        return new IdempotentSaveResult<>(item, false);
    }
}
