package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailNotificationStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationStrategy.class);

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

        return dispatchToProvider(recipient, payload);
    }

    private boolean isValidRecipient(String recipient) {
        return recipient != null && recipient.contains("@");
    }

    private NotificationResult dispatchToProvider(String recipient, String payload) {
        String providerMessageId = "email-" + UUID.randomUUID();
        log.debug("[EMAIL] Mock dispatch accepted — providerMessageId: {}", providerMessageId);
        return NotificationResult.success(providerMessageId);
    }
}