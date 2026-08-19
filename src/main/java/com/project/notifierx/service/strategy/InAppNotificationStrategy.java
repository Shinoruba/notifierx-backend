package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InAppNotificationStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationStrategy.class);

    @Override
    public ChannelType supportsChannel() {
        return ChannelType.IN_APP;
    }

    @Override
    public NotificationResult send(String recipient, String payload) {
        if (!isValidRecipient(recipient)) {
            String reason = "Invalid IN_APP recipient: '" + recipient
                    + "' — user identifier must not be null or blank";
            log.warn("[IN_APP] Validation failure: {}", reason);
            return NotificationResult.failure(reason);
        }

        log.info("[IN_APP] Dispatching to user '{}' — payload length: {} chars",
                recipient, payload == null ? 0 : payload.length());

        return dispatchToProvider(recipient, payload);
    }

    private boolean isValidRecipient(String recipient) {
        return recipient != null && !recipient.isBlank();
    }

    private NotificationResult dispatchToProvider(String recipient, String payload) {
        String providerMessageId = "inapp-" + UUID.randomUUID();
        log.debug("[IN_APP] Mock dispatch accepted — providerMessageId: {}", providerMessageId);
        return NotificationResult.success(providerMessageId);
    }
}