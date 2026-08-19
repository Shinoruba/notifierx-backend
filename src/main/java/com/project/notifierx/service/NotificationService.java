package com.project.notifierx.service;

import com.project.notifierx.dto.SendNotificationRequest;
import com.project.notifierx.dto.SendNotificationResponse;
import com.project.notifierx.exception.RateLimitExceededException;
import com.project.notifierx.exception.UserNotFoundException;

public interface NotificationService {

    SendNotificationResponse sendNotification(String apiKey, SendNotificationRequest request);
}
