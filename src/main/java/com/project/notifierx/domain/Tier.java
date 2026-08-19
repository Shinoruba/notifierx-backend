package com.project.notifierx.domain;

public enum Tier {

    FREE(5),
    PREMIUM(100);

    private final int rateLimitPerMinute;

    Tier(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }
}