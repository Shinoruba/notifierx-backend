package com.project.notifierx.service.strategy;

import java.time.Instant;

public record NotificationResult(
        boolean success,
        String providerMessageId,
        String errorMessage,
        Instant dispatchedAt
) {

    public static NotificationResult success(String providerMessageId) {
        return new NotificationResult(true, providerMessageId, null, Instant.now());
    }

    public static NotificationResult failure(String errorMessage) {
        return new NotificationResult(false, null, errorMessage, Instant.now());
    }
}