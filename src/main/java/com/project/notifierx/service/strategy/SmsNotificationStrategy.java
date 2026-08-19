package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SmsNotificationStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationStrategy.class);

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

        return dispatchToProvider(recipient, payload);
    }

    private boolean isValidRecipient(String recipient) {
        return recipient != null && recipient.startsWith("+");
    }

    private NotificationResult dispatchToProvider(String recipient, String payload) {
        String providerMessageId = "sms-" + UUID.randomUUID();
        log.debug("[SMS] Mock dispatch accepted — providerMessageId: {}", providerMessageId);
        return NotificationResult.success(providerMessageId);
    }
}