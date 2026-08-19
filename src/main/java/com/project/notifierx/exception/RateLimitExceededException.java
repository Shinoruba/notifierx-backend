package com.project.notifierx.exception;

import com.project.notifierx.domain.Tier;

public class RateLimitExceededException extends RuntimeException {

    private final String apiKey;
    private final Tier tier;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String apiKey, Tier tier, long retryAfterSeconds) {
        super(String.format(
                "Rate limit exceeded for API key '%s' (tier: %s). Retry after %d second(s).",
                apiKey, tier, retryAfterSeconds));
        this.apiKey = apiKey;
        this.tier = tier;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Tier getTier() {
        return tier;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}