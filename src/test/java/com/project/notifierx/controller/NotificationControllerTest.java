package com.project.notifierx.controller;

import com.project.notifierx.domain.ChannelType;
import com.project.notifierx.domain.NotificationStatus;
import com.project.notifierx.domain.Tier;
import com.project.notifierx.dto.SendNotificationRequest;
import com.project.notifierx.dto.SendNotificationResponse;
import com.project.notifierx.exception.GlobalExceptionHandler;
import com.project.notifierx.exception.RateLimitExceededException;
import com.project.notifierx.exception.UnsupportedChannelException;
import com.project.notifierx.exception.UserNotFoundException;
import com.project.notifierx.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private static final String API_KEY = "test-api-key-12345";
    private static final String BASE_URL = "/api/v1/notifications";

    @Test
    @DisplayName("POST /api/v1/notifications returns 200 OK with response payload for valid request")
    void sendNotification_returns200Ok_forValidRequest() throws Exception {
        UUID auditId = UUID.randomUUID();
        SendNotificationResponse response = new SendNotificationResponse(
                auditId, NotificationStatus.SENT, "msg-email-987", null, Instant.now());

        when(notificationService.sendNotification(eq(API_KEY), any(SendNotificationRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "channel": "EMAIL",
                    "recipient": "user@example.com",
                    "payload": "Hello from NotifierX!"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditId").value(auditId.toString()))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.providerMessageId").value("msg-email-987"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.processedAt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 400 Bad Request when X-API-Key header is missing")
    void sendNotification_returns400_whenApiKeyHeaderMissing() throws Exception {
        String json = """
                {
                    "channel": "EMAIL",
                    "recipient": "user@example.com",
                    "payload": "Hello!"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing Request Header"))
                .andExpect(jsonPath("$.headerName").value("X-API-Key"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 400 Bad Request with field errors when recipient is blank")
    void sendNotification_returns400_whenRecipientIsBlank() throws Exception {
        String json = """
                {
                    "channel": "SMS",
                    "recipient": "   ",
                    "payload": "Hello!"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.recipient").exists());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 400 Bad Request with field errors when channel is null")
    void sendNotification_returns400_whenChannelIsNull() throws Exception {
        String json = """
                {
                    "channel": null,
                    "recipient": "user@example.com",
                    "payload": "Test payload"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.channel").exists());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 400 Bad Request with field errors when payload is blank")
    void sendNotification_returns400_whenPayloadIsBlank() throws Exception {
        String json = """
                {
                    "channel": "IN_APP",
                    "recipient": "user-123",
                    "payload": ""
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.payload").exists());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 429 Too Many Requests with Retry-After header when rate limited")
    void sendNotification_returns429_whenRateLimitExceeded() throws Exception {
        when(notificationService.sendNotification(eq(API_KEY), any(SendNotificationRequest.class)))
                .thenThrow(new RateLimitExceededException(API_KEY, Tier.FREE, 60L));

        String json = """
                {
                    "channel": "EMAIL",
                    "recipient": "user@example.com",
                    "payload": "Test alert"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
                .andExpect(jsonPath("$.tier").value("FREE"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60))
                .andExpect(jsonPath("$.apiKey").value(API_KEY));
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 401 Unauthorized Problem Details when API key is unknown")
    void sendNotification_returns401_whenUserNotFound() throws Exception {
        when(notificationService.sendNotification(eq(API_KEY), any(SendNotificationRequest.class)))
                .thenThrow(new UserNotFoundException(API_KEY));

        String json = """
                {
                    "channel": "EMAIL",
                    "recipient": "user@example.com",
                    "payload": "Test alert"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("User Not Found"))
                .andExpect(jsonPath("$.apiKey").value(API_KEY))
                .andExpect(jsonPath("$.detail").value("No user found for API key: " + API_KEY));
    }

    @Test
    @DisplayName("POST /api/v1/notifications returns 400 Bad Request Problem Details when channel is unsupported")
    void sendNotification_returns400_whenChannelUnsupported() throws Exception {
        when(notificationService.sendNotification(eq(API_KEY), any(SendNotificationRequest.class)))
                .thenThrow(new UnsupportedChannelException(ChannelType.SMS));

        String json = """
                {
                    "channel": "SMS",
                    "recipient": "+1234567890",
                    "payload": "Test alert"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unsupported Channel"))
                .andExpect(jsonPath("$.channel").value("SMS"));
    }
}