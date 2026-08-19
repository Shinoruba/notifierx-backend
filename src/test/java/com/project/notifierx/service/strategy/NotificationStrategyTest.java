package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStrategyTest {

    @Nested
    @DisplayName("EmailNotificationStrategy")
    class EmailStrategyTests {

        private final EmailNotificationStrategy strategy = new EmailNotificationStrategy();

        @Test
        @DisplayName("supportsChannel() returns EMAIL")
        void supportsChannel_returnsEmail() {
            assertThat(strategy.supportsChannel()).isEqualTo(ChannelType.EMAIL);
        }

        @Test
        @DisplayName("send() succeeds for a valid email address via primary provider")
        void send_succeeds_forValidRecipient() {
            NotificationResult result = strategy.send("user@example.com", "Hello!");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).startsWith("email-primary-");
            assertThat(result.errorMessage()).isNull();
            assertThat(result.dispatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("send() seamlessly routes to fallback provider when primary fails")
        void send_fallsBackToSecondaryProvider_whenPrimaryFails() {
            strategy.setSimulatePrimaryFailure(true);
            try {
                NotificationResult result = strategy.send("user@example.com", "Hello!");

                assertThat(result.success()).isTrue();
                assertThat(result.providerMessageId()).startsWith("email-fallback-");
                assertThat(result.errorMessage()).isNull();
            } finally {
                strategy.resetSimulation();
            }
        }

        @Test
        @DisplayName("send() reports failure when both primary and fallback providers fail")
        void send_fails_whenBothProvidersFail() {
            strategy.setSimulatePrimaryFailure(true);
            strategy.setSimulateSecondaryFailure(true);
            try {
                NotificationResult result = strategy.send("user@example.com", "Hello!");

                assertThat(result.success()).isFalse();
                assertThat(result.providerMessageId()).isNull();
                assertThat(result.errorMessage()).contains("All email providers failed");
            } finally {
                strategy.resetSimulation();
            }
        }

        @Test
        @DisplayName("send() fails when recipient does not contain '@'")
        void send_fails_whenRecipientHasNoAtSign() {
            NotificationResult result = strategy.send("not-an-email", "Hello!");

            assertThat(result.success()).isFalse();
            assertThat(result.providerMessageId()).isNull();
            assertThat(result.errorMessage()).contains("@");
        }

        @Test
        @DisplayName("send() fails for null recipient")
        void send_fails_forNullRecipient() {
            NotificationResult result = strategy.send(null, "Hello!");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("send() fails for blank recipient")
        void send_fails_forBlankRecipient() {
            NotificationResult result = strategy.send("  ", "Hello!");

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("send() sets dispatchedAt on both success and failure")
        void send_setsDispatchedAt_onBothSuccessAndFailure() {
            assertThat(strategy.send("a@b.com", "x").dispatchedAt()).isNotNull();
            assertThat(strategy.send("invalid", "x").dispatchedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("SmsNotificationStrategy")
    class SmsStrategyTests {

        private final SmsNotificationStrategy strategy = new SmsNotificationStrategy();

        @Test
        @DisplayName("supportsChannel() returns SMS")
        void supportsChannel_returnsSms() {
            assertThat(strategy.supportsChannel()).isEqualTo(ChannelType.SMS);
        }

        @Test
        @DisplayName("send() succeeds for an E.164 phone number via primary provider")
        void send_succeeds_forValidE164Recipient() {
            NotificationResult result = strategy.send("+12025550123", "Your OTP is 1234");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).startsWith("sms-primary-");
            assertThat(result.errorMessage()).isNull();
            assertThat(result.dispatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("send() seamlessly routes to fallback provider when primary fails")
        void send_fallsBackToSecondaryProvider_whenPrimaryFails() {
            strategy.setSimulatePrimaryFailure(true);
            try {
                NotificationResult result = strategy.send("+12025550123", "Your OTP is 1234");

                assertThat(result.success()).isTrue();
                assertThat(result.providerMessageId()).startsWith("sms-fallback-");
                assertThat(result.errorMessage()).isNull();
            } finally {
                strategy.resetSimulation();
            }
        }

        @Test
        @DisplayName("send() reports failure when both primary and fallback providers fail")
        void send_fails_whenBothProvidersFail() {
            strategy.setSimulatePrimaryFailure(true);
            strategy.setSimulateSecondaryFailure(true);
            try {
                NotificationResult result = strategy.send("+12025550123", "Your OTP is 1234");

                assertThat(result.success()).isFalse();
                assertThat(result.providerMessageId()).isNull();
                assertThat(result.errorMessage()).contains("All SMS providers failed");
            } finally {
                strategy.resetSimulation();
            }
        }

        @Test
        @DisplayName("send() fails when recipient does not start with '+'")
        void send_fails_whenRecipientHasNoPlusPrefix() {
            NotificationResult result = strategy.send("12025550123", "Your OTP is 1234");

            assertThat(result.success()).isFalse();
            assertThat(result.providerMessageId()).isNull();
            assertThat(result.errorMessage()).contains("+");
        }

        @Test
        @DisplayName("send() fails for null recipient")
        void send_fails_forNullRecipient() {
            NotificationResult result = strategy.send(null, "msg");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("send() fails for a plain word without '+' prefix")
        void send_fails_forPlainWordRecipient() {
            NotificationResult result = strategy.send("mobileNumber", "msg");

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("send() sets dispatchedAt on both success and failure")
        void send_setsDispatchedAt_onBothSuccessAndFailure() {
            assertThat(strategy.send("+447700900123", "x").dispatchedAt()).isNotNull();
            assertThat(strategy.send("bad", "x").dispatchedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("InAppNotificationStrategy")
    class InAppStrategyTests {

        private final InAppNotificationStrategy strategy = new InAppNotificationStrategy();

        @Test
        @DisplayName("supportsChannel() returns IN_APP")
        void supportsChannel_returnsInApp() {
            assertThat(strategy.supportsChannel()).isEqualTo(ChannelType.IN_APP);
        }

        @Test
        @DisplayName("send() succeeds for a valid non-blank user identifier")
        void send_succeeds_forValidRecipient() {
            NotificationResult result = strategy.send("user-uuid-abc123", "You have a new message");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).startsWith("inapp-");
            assertThat(result.errorMessage()).isNull();
            assertThat(result.dispatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("send() fails for null recipient")
        void send_fails_forNullRecipient() {
            NotificationResult result = strategy.send(null, "msg");

            assertThat(result.success()).isFalse();
            assertThat(result.providerMessageId()).isNull();
            assertThat(result.errorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("send() fails for blank recipient")
        void send_fails_forBlankRecipient() {
            NotificationResult result = strategy.send("   ", "msg");

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("blank");
        }

        @Test
        @DisplayName("send() fails for empty string recipient")
        void send_fails_forEmptyRecipient() {
            NotificationResult result = strategy.send("", "msg");

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("send() sets dispatchedAt on both success and failure")
        void send_setsDispatchedAt_onBothSuccessAndFailure() {
            assertThat(strategy.send("user-123", "x").dispatchedAt()).isNotNull();
            assertThat(strategy.send("", "x").dispatchedAt()).isNotNull();
        }
    }

    // ── NotificationResult record ─────────────────────────────────────────────

    @Nested
    @DisplayName("NotificationResult")
    class NotificationResultTests {

        @Test
        @DisplayName("success() factory sets correct fields")
        void success_factory_setsCorrectFields() {
            NotificationResult result = NotificationResult.success("msg-xyz");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("msg-xyz");
            assertThat(result.errorMessage()).isNull();
            assertThat(result.dispatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("failure() factory sets correct fields")
        void failure_factory_setsCorrectFields() {
            NotificationResult result = NotificationResult.failure("SMTP timeout");

            assertThat(result.success()).isFalse();
            assertThat(result.providerMessageId()).isNull();
            assertThat(result.errorMessage()).isEqualTo("SMTP timeout");
            assertThat(result.dispatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("Two success results with different IDs are not equal (record equality)")
        void recordEquality_distinguishesDifferentMessageIds() {
            NotificationResult r1 = NotificationResult.success("id-1");
            NotificationResult r2 = NotificationResult.success("id-2");
            assertThat(r1).isNotEqualTo(r2);
        }
    }
}