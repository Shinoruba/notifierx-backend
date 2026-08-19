package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailNotificationStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationStrategy.class);

    private volatile boolean simulatePrimaryFailure = false;
    private volatile boolean simulateSecondaryFailure = false;

    @Override
    public ChannelType supportsChannel() {
        return ChannelType.EMAIL;
    }

    @Override
    public NotificationResult send(String recipient, String payload) {
        if (!isValidRecipient(recipient)) {
            String reason = "Invalid email recipient: '" + recipient
                    + "' — address must contain '@'";
            log.warn("[EMAIL] Validation failure: {}", reason);
            return NotificationResult.failure(reason);
        }

        log.info("[EMAIL] Dispatching to '{}' — payload length: {} chars",
                recipient, payload == null ? 0 : payload.length());

        try {
            NotificationResult primaryResult = dispatchPrimary(recipient, payload);
            if (primaryResult.success()) {
                log.info("[EMAIL] Primary provider dispatch successful: {}", primaryResult.providerMessageId());
                return primaryResult;
            }
            log.warn("[EMAIL] Primary provider failed: {}. Triggering fallback provider...", primaryResult.errorMessage());
        } catch (Exception ex) {
            log.warn("[EMAIL] Primary provider threw exception: {}. Triggering fallback provider...", ex.getMessage());
        }

        try {
            NotificationResult fallbackResult = dispatchFallback(recipient, payload);
            if (fallbackResult.success()) {
                log.info("[EMAIL] Fallback provider dispatch successful: {}", fallbackResult.providerMessageId());
                return fallbackResult;
            }
            log.error("[EMAIL] Both primary and fallback providers failed for recipient '{}'", recipient);
            return NotificationResult.failure("All email providers failed. Fallback error: " + fallbackResult.errorMessage());
        } catch (Exception ex) {
            log.error("[EMAIL] Fallback provider threw exception for recipient '{}': {}", recipient, ex.getMessage());
            return NotificationResult.failure("All email providers failed. Fallback exception: " + ex.getMessage());
        }
    }

    private boolean isValidRecipient(String recipient) {
        return recipient != null && recipient.contains("@");
    }

    protected NotificationResult dispatchPrimary(String recipient, String payload) {
        if (simulatePrimaryFailure) {
            return NotificationResult.failure("Primary email provider (SendGrid) simulated timeout");
        }
        String providerMessageId = "email-primary-" + UUID.randomUUID();
        log.debug("[EMAIL] Primary dispatch accepted — providerMessageId: {}", providerMessageId);
        return NotificationResult.success(providerMessageId);
    }

    protected NotificationResult dispatchFallback(String recipient, String payload) {
        if (simulateSecondaryFailure) {
            return NotificationResult.failure("Fallback email provider (AWS SES) simulated failure");
        }
        String providerMessageId = "email-fallback-" + UUID.randomUUID();
        log.debug("[EMAIL] Fallback dispatch accepted — providerMessageId: {}", providerMessageId);
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