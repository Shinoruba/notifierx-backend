package com.project.notifierx.controller;

import com.project.notifierx.dto.SendNotificationRequest;
import com.project.notifierx.dto.SendNotificationResponse;
import com.project.notifierx.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<SendNotificationResponse> sendNotification(
            @RequestHeader(value = "X-API-Key", required = true) String apiKey,
            @Valid @RequestBody SendNotificationRequest request) {
        SendNotificationResponse response = notificationService.sendNotification(apiKey, request);
        return ResponseEntity.ok(response);
    }
}