package com.roompick.domain.member.repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class FakeTokenBlacklistRepository implements TokenBlacklistRepository {

    private final Set<String> blacklisted = ConcurrentHashMap.newKeySet();

    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds > 0) {
            blacklisted.add(jti);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return blacklisted.contains(jti);
    }
}
