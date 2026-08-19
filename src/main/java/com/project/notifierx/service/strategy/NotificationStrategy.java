package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;

public interface NotificationStrategy {

    ChannelType supportsChannel();

    NotificationResult send(String recipient, String payload);
}