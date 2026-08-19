package com.project.notifierx.service.ratelimit;

import com.project.notifierx.domain.Tier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisTokenBucketRateLimiterTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<String> keysToClean = new ArrayList<>();


    @AfterEach
    void cleanup() {
        if (!keysToClean.isEmpty()) {
            redisTemplate.delete(keysToClean);
            keysToClean.clear();
        }
    }

    private String uniqueKey(String prefix) {
        String key = prefix + UUID.randomUUID();
        keysToClean.add(RedisTokenBucketRateLimiter.KEY_PREFIX + key);
        return key;
    }

    @Test
    @DisplayName("FREE tier: first 5 requests are all allowed")
    void freeTier_requestsWithinLimit_areAllowed() {
        String apiKey = uniqueKey("free-within-");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            assertThat(rateLimiter.isAllowed(apiKey, Tier.FREE))
                    .as("request #%d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("FREE tier: 6th request within the window is denied")
    void freeTier_requestExceedingLimit_isDenied() {
        String apiKey = uniqueKey("free-exceed-");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(apiKey, Tier.FREE);
        }

        assertThat(rateLimiter.isAllowed(apiKey, Tier.FREE))
                .as("6th request should be denied")
                .isFalse();
    }

    @Test
    @DisplayName("FREE tier: getRemainingTokens returns 0 after bucket exhaustion")
    void freeTier_remainingTokens_areZeroAfterExhaustion() {
        String apiKey = uniqueKey("free-remaining-");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(apiKey, Tier.FREE);
        }

        assertThat(rateLimiter.getRemainingTokens(apiKey, Tier.FREE)).isZero();
    }

    @Test
    @DisplayName("FREE tier: getRemainingTokens decrements correctly with each request")
    void freeTier_remainingTokens_decrementWithEachRequest() {
        String apiKey = uniqueKey("free-decrement-");
        int capacity = Tier.FREE.getRateLimitPerMinute(); // 5

        for (int consumed = 1; consumed <= capacity; consumed++) {
            rateLimiter.isAllowed(apiKey, Tier.FREE);
            long expected = capacity - consumed;
            assertThat(rateLimiter.getRemainingTokens(apiKey, Tier.FREE))
                    .as("after %d requests, remaining should be %d", consumed, expected)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("PREMIUM tier: first 100 requests are all allowed")
    void premiumTier_requestsWithinLimit_areAllowed() {
        String apiKey = uniqueKey("premium-within-");

        for (int i = 0; i < Tier.PREMIUM.getRateLimitPerMinute(); i++) {
            assertThat(rateLimiter.isAllowed(apiKey, Tier.PREMIUM))
                    .as("request #%d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("PREMIUM tier: 101st request within the window is denied")
    void premiumTier_requestExceedingLimit_isDenied() {
        String apiKey = uniqueKey("premium-exceed-");

        for (int i = 0; i < Tier.PREMIUM.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(apiKey, Tier.PREMIUM);
        }

        assertThat(rateLimiter.isAllowed(apiKey, Tier.PREMIUM))
                .as("101st request should be denied")
                .isFalse();
    }

    @Test
    @DisplayName("FREE and PREMIUM tiers use independent buckets and enforce distinct capacities")
    void tierIsolation_freeAndPremium_maintainIndependentBuckets() {
        String freeKey    = uniqueKey("isolation-free-");
        String premiumKey = uniqueKey("isolation-premium-");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(freeKey, Tier.FREE);
        }

        assertThat(rateLimiter.isAllowed(freeKey, Tier.FREE))
                .as("FREE bucket should be exhausted").isFalse();
        assertThat(rateLimiter.isAllowed(premiumKey, Tier.PREMIUM))
                .as("PREMIUM bucket should be independent and allow requests").isTrue();
    }

    @Test
    @DisplayName("FREE bucket exhaustion does not affect a different FREE-tier client")
    void tierIsolation_twoFreeClients_maintainIndependentBuckets() {
        String apiKeyA = uniqueKey("free-client-a-");
        String apiKeyB = uniqueKey("free-client-b-");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(apiKeyA, Tier.FREE);
        }

        assertThat(rateLimiter.isAllowed(apiKeyA, Tier.FREE))
                .as("Client A should be exhausted").isFalse();
        assertThat(rateLimiter.isAllowed(apiKeyB, Tier.FREE))
                .as("Client B should be unaffected").isTrue();
    }


    @Test
    @DisplayName("Tokens replenish proportionally after time elapses within the window")
    void tokens_replenish_afterElapsedTime() {
        String apiKey = uniqueKey("replenish-partial-");
        String redisKey = RedisTokenBucketRateLimiter.KEY_PREFIX + apiKey;

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(apiKey, Tier.FREE);
        }
        assertThat(rateLimiter.isAllowed(apiKey, Tier.FREE)).isFalse();

        long pastTime = System.currentTimeMillis() - 61_000L;
        redisTemplate.opsForHash().put(redisKey, "last_refill_at", String.valueOf(pastTime));

        assertThat(rateLimiter.isAllowed(apiKey, Tier.FREE))
                .as("request should be allowed after full window elapsed")
                .isTrue();
        assertThat(rateLimiter.getRemainingTokens(apiKey, Tier.FREE))
                .as("remaining tokens after 1 consumed from full bucket")
                .isEqualTo(Tier.FREE.getRateLimitPerMinute() - 1);
    }

    @Test
    @DisplayName("getRemainingTokens returns full capacity for a brand-new key")
    void getRemainingTokens_returnsFullCapacity_forNewKey() {
        String apiKey = uniqueKey("peek-new-");

        assertThat(rateLimiter.getRemainingTokens(apiKey, Tier.FREE))
                .isEqualTo(Tier.FREE.getRateLimitPerMinute());
        assertThat(rateLimiter.getRemainingTokens(apiKey, Tier.PREMIUM))
                .isEqualTo(Tier.PREMIUM.getRateLimitPerMinute());
    }

    @Test
    @DisplayName("Denied requests do not consume tokens from the exhausted bucket")
    void deniedRequests_doNotConsumeTokens() {
        String apiKey = uniqueKey("no-consume-on-deny-");

        for (int i = 0; i < Tier.FREE.getRateLimitPerMinute(); i++) {
            rateLimiter.isAllowed(apiKey, Tier.FREE);
        }

        long remainingBefore = rateLimiter.getRemainingTokens(apiKey, Tier.FREE);

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.isAllowed(apiKey, Tier.FREE)).isFalse();
        }

        assertThat(rateLimiter.getRemainingTokens(apiKey, Tier.FREE))
                .as("denied requests must not further reduce the token count")
                .isEqualTo(remainingBefore);
    }
}