package com.project.notifierx.dto;

import com.project.notifierx.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record SendNotificationResponse(
        UUID auditId,
        NotificationStatus status,
        String providerMessageId,
        String errorMessage,
        Instant processedAt
) {}