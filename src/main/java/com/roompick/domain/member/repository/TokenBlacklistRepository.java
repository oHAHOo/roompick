package com.roompick.domain.member.repository;

public interface TokenBlacklistRepository {

    /**
     * jti를 원자적으로 블랙리스트에 등록합니다.
     * 이미 등록되어 있었다면 false를 반환하여 동시 요청 중 단 하나만 성공하도록 보장합니다.
     */
    boolean consume(String jti, long ttlSeconds);

    boolean isBlacklisted(String jti);
}
