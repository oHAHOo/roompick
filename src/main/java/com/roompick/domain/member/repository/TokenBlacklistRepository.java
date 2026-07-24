package com.roompick.domain.member.repository;

public interface TokenBlacklistRepository {

    void blacklist(String jti, long ttlSeconds);

    boolean isBlacklisted(String jti);
}
