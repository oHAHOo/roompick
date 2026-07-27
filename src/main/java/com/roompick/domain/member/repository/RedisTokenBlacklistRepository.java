package com.roompick.domain.member.repository;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@Profile("!test")
@RequiredArgsConstructor
public class RedisTokenBlacklistRepository implements TokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean consume(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return false;
        }

        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key(jti), "1", Duration.ofSeconds(ttlSeconds));

        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
