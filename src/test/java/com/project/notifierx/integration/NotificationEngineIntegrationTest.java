package com.project.notifierx.integration;

import com.project.notifierx.domain.ChannelType;
import com.project.notifierx.domain.NotificationAudit;
import com.project.notifierx.domain.NotificationStatus;
import com.project.notifierx.domain.Tier;
import com.project.notifierx.domain.User;
import com.project.notifierx.dto.SendNotificationRequest;
import com.project.notifierx.dto.SendNotificationResponse;
import com.project.notifierx.repository.NotificationAuditRepository;
import com.project.notifierx.repository.UserRepository;
import com.project.notifierx.service.strategy.EmailNotificationStrategy;
import com.project.notifierx.service.strategy.SmsNotificationStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationEngineIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationAuditRepository auditRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EmailNotificationStrategy emailStrategy;

    @Autowired
    private SmsNotificationStrategy smsStrategy;

    private static final String NOTIFICATIONS_PATH = "/api/v1/notifications";

    private final List<String> createdRedisKeys = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });

        emailStrategy.resetSimulation();
        smsStrategy.resetSimulation();
    }

    @AfterEach
    void tearDown() {
        emailStrategy.resetSimulation();
        smsStrategy.resetSimulation();

        if (!createdRedisKeys.isEmpty()) {
            redisTemplate.delete(createdRedisKeys);
            createdRedisKeys.clear();
        }

        for (UUID userId : createdUserIds) {
            List<NotificationAudit> audits = auditRepository.findAll().stream()
                    .filter(a -> userId.equals(a.getUserId()))
                    .toList();
            auditRepository.deleteAll(audits);
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
    }

    private User createTestUser(String name, Tier tier) {
        String apiKey = "api-key-" + UUID.randomUUID();
        User user = User.builder()
                .name(name)
                .apiKey(apiKey)
                .tier(tier)
                .build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        createdRedisKeys.add("rate_limit:" + apiKey);
        return saved;
    }

    private <T> ResponseEntity<T> sendPost(String apiKey, SendNotificationRequest request, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            headers.set("X-API-Key", apiKey);
        }
        HttpEntity<SendNotificationRequest> entity = new HttpEntity<>(request, headers);
        String url = "http://localhost:" + port + NOTIFICATIONS_PATH;
        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
    }

    @Test
    @DisplayName("Valid notification returns 200 OK, decrements Redis token count, and writes SENT audit to PostgreSQL")
    void validNotification_dispatchesSuccessfully_andUpdatesState() {
        User user = createTestUser("Integration User Free", Tier.FREE);
        SendNotificationRequest request = new SendNotificationRequest(
                ChannelType.EMAIL, "recipient@example.com", "E2E Test Payload");

        ResponseEntity<SendNotificationResponse> response = sendPost(
                user.getApiKey(), request, SendNotificationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SendNotificationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(body.providerMessageId()).startsWith("email-primary-");
        assertThat(body.errorMessage()).isNull();
        assertThat(body.auditId()).isNotNull();

        Optional<NotificationAudit> savedAudit = auditRepository.findById(body.auditId());
        assertThat(savedAudit).isPresent();
        assertThat(savedAudit.get().getUserId()).isEqualTo(user.getId());
        assertThat(savedAudit.get().getChannel()).isEqualTo(ChannelType.EMAIL);
        assertThat(savedAudit.get().getRecipient()).isEqualTo("recipient@example.com");
        assertThat(savedAudit.get().getPayload()).isEqualTo("E2E Test Payload");
        assertThat(savedAudit.get().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(savedAudit.get().getSentAt()).isNotNull();

        String redisKey = "rate_limit:" + user.getApiKey();
        Object tokens = redisTemplate.opsForHash().get(redisKey, "tokens");
        assertThat(tokens).isNotNull();
        assertThat(Long.parseLong(tokens.toString())).isEqualTo(Tier.FREE.getRateLimitPerMinute() - 1);
    }

    @Test
    @DisplayName("Bursting requests beyond FREE tier limit (5) triggers HTTP 429 Too Many Requests with Retry-After header")
    void burstingRequests_exceedingTierLimit_returns429TooManyRequests() {
        User user = createTestUser("Burst User", Tier.FREE);
        SendNotificationRequest request = new SendNotificationRequest(
                ChannelType.EMAIL, "burst@example.com", "Alert");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            ResponseEntity<SendNotificationResponse> okResp = sendPost(
                    user.getApiKey(), request, SendNotificationResponse.class);
            assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> rateLimitedResp = sendPost(
                user.getApiKey(), request, String.class);

        assertThat(rateLimitedResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimitedResp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

        String body = rateLimitedResp.getBody();
        if (body != null) {
            assertThat(body).contains("Rate Limit Exceeded");
            assertThat(body).contains("FREE");
        }

        long auditCount = auditRepository.findAll().stream()
                .filter(a -> user.getId().equals(a.getUserId()))
                .count();
        assertThat(auditCount).isEqualTo(5);
    }

    @Test
    @DisplayName("Unknown API key returns HTTP 401 Unauthorized and does not alter Redis or DB")
    void unknownApiKey_returns401Unauthorized_andDoesNotAlterState() {
        String unknownApiKey = "non-existent-api-key-" + UUID.randomUUID();
        createdRedisKeys.add("rate_limit:" + unknownApiKey);
        long initialAuditCount = auditRepository.count();

        SendNotificationRequest request = new SendNotificationRequest(
                ChannelType.EMAIL, "test@example.com", "Test");

        ResponseEntity<String> response = sendPost(
                unknownApiKey, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(auditRepository.count()).isEqualTo(initialAuditCount);
        Boolean keyExists = redisTemplate.hasKey("rate_limit:" + unknownApiKey);
        assertThat(keyExists).isFalse();
    }

    @Test
    @DisplayName("Simulated primary provider failure triggers automated secondary fallback and records SENT audit")
    void simulatedPrimaryFailure_triggersAutomatedFallback() {
        User user = createTestUser("Fallback User", Tier.PREMIUM);
        emailStrategy.setSimulatePrimaryFailure(true);

        SendNotificationRequest request = new SendNotificationRequest(
                ChannelType.EMAIL, "resilient@example.com", "High priority alert");

        ResponseEntity<SendNotificationResponse> response = sendPost(
                user.getApiKey(), request, SendNotificationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SendNotificationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(body.providerMessageId()).startsWith("email-fallback-");

        Optional<NotificationAudit> savedAudit = auditRepository.findById(body.auditId());
        assertThat(savedAudit).isPresent();
        assertThat(savedAudit.get().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("SMS channel dispatches successfully with primary provider")
    void validSmsNotification_dispatchesSuccessfully() {
        User user = createTestUser("SMS User", Tier.PREMIUM);
        SendNotificationRequest request = new SendNotificationRequest(
                ChannelType.SMS, "+12025550199", "Your verification code is 5849");

        ResponseEntity<SendNotificationResponse> response = sendPost(
                user.getApiKey(), request, SendNotificationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SendNotificationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(body.providerMessageId()).startsWith("sms-primary-");
    }

    @Test
    @DisplayName("Simulated SMS primary provider failure routes to SMS fallback provider")
    void simulatedSmsPrimaryFailure_triggersSmsFallback() {
        User user = createTestUser("SMS Fallback User", Tier.PREMIUM);
        smsStrategy.setSimulatePrimaryFailure(true);

        SendNotificationRequest request = new SendNotificationRequest(
                ChannelType.SMS, "+12025550188", "Fallback SMS Alert");

        ResponseEntity<SendNotificationResponse> response = sendPost(
                user.getApiKey(), request, SendNotificationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SendNotificationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(body.providerMessageId()).startsWith("sms-fallback-");
    }
}