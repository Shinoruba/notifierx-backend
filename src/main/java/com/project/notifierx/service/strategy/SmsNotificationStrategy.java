package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SmsNotificationStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationStrategy.class);

    private volatile boolean simulatePrimaryFailure = false;
    private volatile boolean simulateSecondaryFailure = false;

    @Override
    public ChannelType supportsChannel() {
        return ChannelType.SMS;
    }

    @Override
    public NotificationResult send(String recipient, String payload) {
        if (!isValidRecipient(recipient)) {
            String reason = "Invalid SMS recipient: '" + recipient
                    + "' — phone number must start with '+' (E.164 format)";
            log.warn("[SMS] Validation failure: {}", reason);
            return NotificationResult.failure(reason);
        }

        log.info("[SMS] Dispatching to '{}' — payload length: {} chars",
                recipient, payload == null ? 0 : payload.length());

        try {
            NotificationResult primaryResult = dispatchPrimary(recipient, payload);
            if (primaryResult.success()) {
                log.info("[SMS] Primary provider dispatch successful: {}", primaryResult.providerMessageId());
                return primaryResult;
            }
            log.warn("[SMS] Primary provider failed: {}. Triggering fallback provider...", primaryResult.errorMessage());
        } catch (Exception ex) {
            log.warn("[SMS] Primary provider threw exception: {}. Triggering fallback provider...", ex.getMessage());
        }

        try {
            NotificationResult fallbackResult = dispatchFallback(recipient, payload);
            if (fallbackResult.success()) {
                log.info("[SMS] Fallback provider dispatch successful: {}", fallbackResult.providerMessageId());
                return fallbackResult;
            }
            log.error("[SMS] Both primary and fallback providers failed for recipient '{}'", recipient);
            return NotificationResult.failure("All SMS providers failed. Fallback error: " + fallbackResult.errorMessage());
        } catch (Exception ex) {
            log.error("[SMS] Fallback provider threw exception for recipient '{}': {}", recipient, ex.getMessage());
            return NotificationResult.failure("All SMS providers failed. Fallback exception: " + ex.getMessage());
        }
    }

    private boolean isValidRecipient(String recipient) {
        return recipient != null && recipient.startsWith("+");
    }

    protected NotificationResult dispatchPrimary(String recipient, String payload) {
        if (simulatePrimaryFailure) {
            return NotificationResult.failure("Primary SMS provider (Twilio) simulated timeout");
        }
        String providerMessageId = "sms-primary-" + UUID.randomUUID();
        log.debug("[SMS] Primary dispatch accepted — providerMessageId: {}", providerMessageId);
        return NotificationResult.success(providerMessageId);
    }

    protected NotificationResult dispatchFallback(String recipient, String payload) {
        if (simulateSecondaryFailure) {
            return NotificationResult.failure("Fallback SMS provider (AWS SNS) simulated failure");
        }
        String providerMessageId = "sms-fallback-" + UUID.randomUUID();
        log.debug("[SMS] Fallback dispatch accepted — providerMessageId: {}", providerMessageId);
        return NotificationResult.success(providerMessageId);
    }

    public void setSimulatePrimaryFailure(boolean simulatePrimaryFailure) {
        this.simulatePrimaryFailure = simulatePrimaryFailure;
    }

    public void setSimulateSecondaryFailure(boolean simulateSecondaryFailure) {
        this.simulateSecondaryFailure = simulateSecondaryFailure;
    }

    public void resetSimulation() {
        this.simulatePrimaryFailure = false;
        this.simulateSecondaryFailure = false;
    }
}