package com.project.notifierx.service.ratelimit;

import com.project.notifierx.domain.Tier;

public interface RateLimiter {

    boolean isAllowed(String apiKey, Tier tier);

    long getRemainingTokens(String apiKey, Tier tier);
}