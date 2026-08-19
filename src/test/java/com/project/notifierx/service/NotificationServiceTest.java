package com.project.notifierx.service;

import com.project.notifierx.domain.*;
import com.project.notifierx.dto.SendNotificationRequest;
import com.project.notifierx.dto.SendNotificationResponse;
import com.project.notifierx.exception.RateLimitExceededException;
import com.project.notifierx.exception.UserNotFoundException;
import com.project.notifierx.repository.NotificationAuditRepository;
import com.project.notifierx.repository.UserRepository;
import com.project.notifierx.service.ratelimit.RateLimiter;
import com.project.notifierx.service.strategy.NotificationResult;
import com.project.notifierx.service.strategy.NotificationStrategy;
import com.project.notifierx.service.strategy.NotificationStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationAuditRepository auditRepository;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private NotificationStrategyFactory strategyFactory;

    @Mock
    private NotificationStrategy strategy;

    @InjectMocks
    private NotificationServiceImpl service;

    private static final String API_KEY   = "test-api-key-abc";
    private static final String RECIPIENT = "user@example.com";
    private static final String PAYLOAD   = "Hello, world!";
    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final UUID   AUDIT_ID  = UUID.randomUUID();

    private User freeUser;
    private User premiumUser;
    private SendNotificationRequest emailRequest;

    @BeforeEach
    void setUp() {
        freeUser = User.builder()
                .id(USER_ID)
                .name("Test User")
                .apiKey(API_KEY)
                .tier(Tier.FREE)
                .build();

        premiumUser = User.builder()
                .id(USER_ID)
                .name("Premium User")
                .apiKey(API_KEY)
                .tier(Tier.PREMIUM)
                .build();

        emailRequest = new SendNotificationRequest(
                ChannelType.EMAIL, RECIPIENT, PAYLOAD);
    }

    private NotificationAudit savedAudit(NotificationStatus status, String errorMessage) {
        return NotificationAudit.builder()
                .id(AUDIT_ID)
                .userId(USER_ID)
                .channel(ChannelType.EMAIL)
                .recipient(RECIPIENT)
                .payload(PAYLOAD)
                .status(status)
                .errorMessage(errorMessage)
                .sentAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Success path")
    class SuccessPath {

        @Test
        @DisplayName("returns SENT response with providerMessageId when dispatch succeeds")
        void sendNotification_returnsSentResponse_onSuccessfulDispatch() {
            String messageId = "email-provider-id-xyz";
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD)).thenReturn(NotificationResult.success(messageId));
            when(auditRepository.save(any())).thenReturn(savedAudit(NotificationStatus.SENT, null));

            SendNotificationResponse response = service.sendNotification(API_KEY, emailRequest);

            assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(response.providerMessageId()).isEqualTo(messageId);
            assertThat(response.errorMessage()).isNull();
            assertThat(response.auditId()).isEqualTo(AUDIT_ID);
            assertThat(response.processedAt()).isNotNull();
        }

        @Test
        @DisplayName("persists a SENT audit record with correct fields on success")
        void sendNotification_persistsSentAudit_withCorrectFields() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD))
                    .thenReturn(NotificationResult.success("msg-123"));
            when(auditRepository.save(any())).thenReturn(savedAudit(NotificationStatus.SENT, null));

            service.sendNotification(API_KEY, emailRequest);

            ArgumentCaptor<NotificationAudit> captor =
                    ArgumentCaptor.forClass(NotificationAudit.class);
            verify(auditRepository).save(captor.capture());

            NotificationAudit captured = captor.getValue();
            assertThat(captured.getUserId()).isEqualTo(USER_ID);
            assertThat(captured.getChannel()).isEqualTo(ChannelType.EMAIL);
            assertThat(captured.getRecipient()).isEqualTo(RECIPIENT);
            assertThat(captured.getPayload()).isEqualTo(PAYLOAD);
            assertThat(captured.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(captured.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("pipeline calls collaborators in the correct order")
        void sendNotification_callsCollaborators_inCorrectOrder() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD))
                    .thenReturn(NotificationResult.success("msg-abc"));
            when(auditRepository.save(any())).thenReturn(savedAudit(NotificationStatus.SENT, null));

            service.sendNotification(API_KEY, emailRequest);

            var inOrder = inOrder(userRepository, rateLimiter, strategyFactory, strategy, auditRepository);
            inOrder.verify(userRepository).findByApiKey(API_KEY);
            inOrder.verify(rateLimiter).isAllowed(API_KEY, Tier.FREE);
            inOrder.verify(strategyFactory).getStrategy(ChannelType.EMAIL);
            inOrder.verify(strategy).send(RECIPIENT, PAYLOAD);
            inOrder.verify(auditRepository).save(any());
        }

        @Test
        @DisplayName("PREMIUM tier: rate limiter called with PREMIUM tier")
        void sendNotification_callsRateLimiter_withCorrectTier_forPremiumUser() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(premiumUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.PREMIUM)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD))
                    .thenReturn(NotificationResult.success("msg-premium"));
            when(auditRepository.save(any())).thenReturn(savedAudit(NotificationStatus.SENT, null));

            service.sendNotification(API_KEY, emailRequest);

            verify(rateLimiter).isAllowed(API_KEY, Tier.PREMIUM);
        }
    }

    @Nested
    @DisplayName("Provider failure path")
    class ProviderFailurePath {

        @Test
        @DisplayName("returns FAILED response with errorMessage when provider fails")
        void sendNotification_returnsFailedResponse_whenProviderFails() {
            String errorMsg = "SMTP connection refused";
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD))
                    .thenReturn(NotificationResult.failure(errorMsg));
            when(auditRepository.save(any()))
                    .thenReturn(savedAudit(NotificationStatus.FAILED, errorMsg));

            SendNotificationResponse response = service.sendNotification(API_KEY, emailRequest);

            assertThat(response.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(response.errorMessage()).isEqualTo(errorMsg);
            assertThat(response.providerMessageId()).isNull();
            assertThat(response.auditId()).isEqualTo(AUDIT_ID);
        }

        @Test
        @DisplayName("persists a FAILED audit record with errorMessage when provider fails")
        void sendNotification_persistsFailedAudit_withErrorMessage() {
            String errorMsg = "Invalid recipient format";
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD))
                    .thenReturn(NotificationResult.failure(errorMsg));
            when(auditRepository.save(any()))
                    .thenReturn(savedAudit(NotificationStatus.FAILED, errorMsg));

            service.sendNotification(API_KEY, emailRequest);

            ArgumentCaptor<NotificationAudit> captor =
                    ArgumentCaptor.forClass(NotificationAudit.class);
            verify(auditRepository).save(captor.capture());

            NotificationAudit captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(captured.getErrorMessage()).isEqualTo(errorMsg);
        }

        @Test
        @DisplayName("audit is always persisted even when dispatch fails")
        void sendNotification_alwaysPersistsAudit_evenOnDispatchFailure() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.EMAIL)).thenReturn(strategy);
            when(strategy.send(RECIPIENT, PAYLOAD))
                    .thenReturn(NotificationResult.failure("timeout"));
            when(auditRepository.save(any()))
                    .thenReturn(savedAudit(NotificationStatus.FAILED, "timeout"));

            service.sendNotification(API_KEY, emailRequest);

            verify(auditRepository, times(1)).save(any(NotificationAudit.class));
        }
    }

    @Nested
    @DisplayName("UserNotFoundException")
    class UserNotFoundPath {

        @Test
        @DisplayName("throws UserNotFoundException when API key is not registered")
        void sendNotification_throwsUserNotFoundException_forUnknownApiKey() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendNotification(API_KEY, emailRequest))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(API_KEY);
        }

        @Test
        @DisplayName("rate limiter is never called when user is not found")
        void sendNotification_neverCallsRateLimiter_whenUserNotFound() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendNotification(API_KEY, emailRequest))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(rateLimiter);
        }

        @Test
        @DisplayName("dispatch and audit are never called when user is not found")
        void sendNotification_neverDispatchesOrAudits_whenUserNotFound() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sendNotification(API_KEY, emailRequest))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(strategyFactory, strategy, auditRepository);
        }

        @Test
        @DisplayName("UserNotFoundException carries the API key")
        void sendNotification_exceptionCarriesApiKey_whenUserNotFound() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.empty());

            UserNotFoundException ex = catchThrowableOfType(
                    () -> service.sendNotification(API_KEY, emailRequest),
                    UserNotFoundException.class);
            assertThat(ex.getApiKey()).isEqualTo(API_KEY);
        }
    }

    @Nested
    @DisplayName("RateLimitExceededException")
    class RateLimitExceededPath {

        @Test
        @DisplayName("throws RateLimitExceededException when token bucket is exhausted")
        void sendNotification_throwsRateLimitExceededException_whenBucketExhausted() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(false);

            assertThatThrownBy(() -> service.sendNotification(API_KEY, emailRequest))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining(API_KEY);
        }

        @Test
        @DisplayName("dispatch is never called when rate limit is exceeded")
        void sendNotification_neverDispatches_whenRateLimitExceeded() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(false);

            assertThatThrownBy(() -> service.sendNotification(API_KEY, emailRequest))
                    .isInstanceOf(RateLimitExceededException.class);

            verifyNoInteractions(strategyFactory, strategy);
        }

        @Test
        @DisplayName("audit is never persisted when rate limit is exceeded")
        void sendNotification_neverPersistsAudit_whenRateLimitExceeded() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(false);

            assertThatThrownBy(() -> service.sendNotification(API_KEY, emailRequest))
                    .isInstanceOf(RateLimitExceededException.class);

            verifyNoInteractions(auditRepository);
        }

        @Test
        @DisplayName("RateLimitExceededException carries apiKey, tier, and retryAfterSeconds")
        void sendNotification_exceptionCarriesStructuredContext_whenRateLimitExceeded() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(false);

            RateLimitExceededException ex = catchThrowableOfType(
                    () -> service.sendNotification(API_KEY, emailRequest),
                    RateLimitExceededException.class);

            assertThat(ex.getApiKey()).isEqualTo(API_KEY);
            assertThat(ex.getTier()).isEqualTo(Tier.FREE);
            assertThat(ex.getRetryAfterSeconds()).isPositive();
        }

        @Test
        @DisplayName("rate limit enforcement uses the user's actual tier")
        void sendNotification_enforcesRateLimit_usingUserActualTier() {
            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(premiumUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.PREMIUM)).thenReturn(false);

            RateLimitExceededException ex = catchThrowableOfType(
                    () -> service.sendNotification(API_KEY, emailRequest),
                    RateLimitExceededException.class);

            assertThat(ex.getTier()).isEqualTo(Tier.PREMIUM);
            verify(rateLimiter).isAllowed(API_KEY, Tier.PREMIUM);
        }
    }

    @Nested
    @DisplayName("Multi-channel routing")
    class ChannelRouting {

        @Test
        @DisplayName("SMS request routes to SMS strategy and produces SENT audit")
        void sendNotification_routesToSmsStrategy_forSmsChannel() {
            SendNotificationRequest smsRequest =
                    new SendNotificationRequest(ChannelType.SMS, "+12025550123", "OTP: 4321");
            NotificationAudit smsAudit = NotificationAudit.builder()
                    .id(AUDIT_ID).userId(USER_ID).channel(ChannelType.SMS)
                    .recipient("+12025550123").payload("OTP: 4321")
                    .status(NotificationStatus.SENT).sentAt(Instant.now()).build();

            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.SMS)).thenReturn(strategy);
            when(strategy.send("+12025550123", "OTP: 4321"))
                    .thenReturn(NotificationResult.success("sms-provider-id"));
            when(auditRepository.save(any())).thenReturn(smsAudit);

            SendNotificationResponse response = service.sendNotification(API_KEY, smsRequest);

            verify(strategyFactory).getStrategy(ChannelType.SMS);
            assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("IN_APP request routes to IN_APP strategy")
        void sendNotification_routesToInAppStrategy_forInAppChannel() {
            SendNotificationRequest inAppRequest =
                    new SendNotificationRequest(ChannelType.IN_APP, "user-uuid-001", "New badge");
            NotificationAudit inAppAudit = NotificationAudit.builder()
                    .id(AUDIT_ID).userId(USER_ID).channel(ChannelType.IN_APP)
                    .recipient("user-uuid-001").payload("New badge")
                    .status(NotificationStatus.SENT).sentAt(Instant.now()).build();

            when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(freeUser));
            when(rateLimiter.isAllowed(API_KEY, Tier.FREE)).thenReturn(true);
            when(strategyFactory.getStrategy(ChannelType.IN_APP)).thenReturn(strategy);
            when(strategy.send("user-uuid-001", "New badge"))
                    .thenReturn(NotificationResult.success("inapp-provider-id"));
            when(auditRepository.save(any())).thenReturn(inAppAudit);

            service.sendNotification(API_KEY, inAppRequest);

            verify(strategyFactory).getStrategy(ChannelType.IN_APP);
        }
    }
}