package com.project.notifierx.service.ratelimit;

import com.project.notifierx.domain.Tier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class RedisTokenBucketRateLimiter implements RateLimiter {


    static final String KEY_PREFIX = "rate_limit:";
    private static final long WINDOW_MS = 60_000L;



    private static final DefaultRedisScript<List<Long>> CONSUME_SCRIPT;


    private static final DefaultRedisScript<Long> PEEK_SCRIPT;

    static {
        CONSUME_SCRIPT = new DefaultRedisScript<>();
        CONSUME_SCRIPT.setResultType((Class<List<Long>>) (Class<?>) List.class);
        CONSUME_SCRIPT.setScriptText("""
                local key          = KEYS[1]
                local capacity     = tonumber(ARGV[1])
                local now_ms       = tonumber(ARGV[2])
                local window_ms    = tonumber(ARGV[3])

                local data         = redis.call('HMGET', key, 'tokens', 'last_refill_at')
                local cur_tokens   = tonumber(data[1])
                local last_refill  = tonumber(data[2])

                if cur_tokens == nil then
                    -- Brand-new bucket: initialise full, then consume one immediately.
                    local remaining = capacity - 1
                    redis.call('HMSET', key, 'tokens', remaining, 'last_refill_at', now_ms)
                    redis.call('PEXPIRE', key, window_ms)
                    return {1, remaining}
                end

                -- Time-based token refill (pro-rated over the window).
                local elapsed    = now_ms - last_refill
                local refill     = math.floor((elapsed * capacity) / window_ms)

                if refill > 0 then
                    cur_tokens  = math.min(capacity, cur_tokens + refill)
                    last_refill = now_ms
                end

                -- Consume one token if available.
                local allowed   = 0
                local remaining = cur_tokens
                if cur_tokens > 0 then
                    remaining = cur_tokens - 1
                    allowed   = 1
                end

                redis.call('HMSET', key, 'tokens', remaining, 'last_refill_at', last_refill)
                redis.call('PEXPIRE', key, window_ms)
                return {allowed, remaining}
                """);

        PEEK_SCRIPT = new DefaultRedisScript<>();
        PEEK_SCRIPT.setResultType(Long.class);
        PEEK_SCRIPT.setScriptText("""
                local key         = KEYS[1]
                local capacity    = tonumber(ARGV[1])
                local now_ms      = tonumber(ARGV[2])
                local window_ms   = tonumber(ARGV[3])

                local data        = redis.call('HMGET', key, 'tokens', 'last_refill_at')
                local cur_tokens  = tonumber(data[1])
                local last_refill = tonumber(data[2])

                if cur_tokens == nil then
                    -- Key does not exist: bucket would start full.
                    return capacity
                end

                -- Apply pending refill (read-only: state is NOT updated).
                local elapsed   = now_ms - last_refill
                local refill    = math.floor((elapsed * capacity) / window_ms)
                return math.min(capacity, cur_tokens + refill)
                """);
    }


    private final StringRedisTemplate redisTemplate;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }



    @Override
    public boolean isAllowed(String apiKey, Tier tier) {
        String key = KEY_PREFIX + apiKey;
        long capacity = tier.getRateLimitPerMinute();
        long nowMs = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(nowMs),
                String.valueOf(WINDOW_MS)
        );

        if (result == null || result.isEmpty()) {
            return false;
        }
        return result.get(0) == 1L;
    }

    @Override
    public long getRemainingTokens(String apiKey, Tier tier) {
        String key = KEY_PREFIX + apiKey;
        long capacity = tier.getRateLimitPerMinute();
        long nowMs = System.currentTimeMillis();

        Long remaining = redisTemplate.execute(
                PEEK_SCRIPT,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(nowMs),
                String.valueOf(WINDOW_MS)
        );

        return remaining != null ? remaining : capacity;
    }
}