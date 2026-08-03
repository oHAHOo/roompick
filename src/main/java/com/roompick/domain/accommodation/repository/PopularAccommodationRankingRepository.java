package com.roompick.domain.accommodation.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Redis Sorted Set을 사용하여 일간 인기 숙소 점수를 관리합니다.
 *
 * Redis 키 생성은 별도의 KeyGenerator가 담당하고,
 * 이 클래스는 Redis 명령 실행만 담당합니다.
 */
@Repository
@RequiredArgsConstructor
public class PopularAccommodationRankingRepository {

    private static final String VIEW_SCORE = "1";

    /**
     * 일간 인기 숙소 키는 이틀 동안 유지합니다.
     */
    private static final String KEY_TTL_SECONDS = "172800";

    /**
     * 조회 점수 증가와 TTL 설정을 하나의 Redis 명령 흐름으로 처리합니다.
     *
     * Lua Script는 Redis 내부에서 원자적으로 실행되므로,
     * 점수만 증가하고 TTL은 설정되지 않는 중간 상태를 방지합니다.
     */
    private static final DefaultRedisScript<Long> INCREMENT_SCORE_SCRIPT =
        new DefaultRedisScript<>(
            """
            redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2])

            local ttl = redis.call('TTL', KEYS[1])

            if ttl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
            end

            return 1
            """,
            Long.class
        );

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 해당 숙소의 일간 조회 점수를 1 증가시킵니다.
     *
     * Sorted Set 구조:
     * - key: 일간 인기 숙소 Redis 키
     * - member: 숙소 ID
     * - score: 누적 조회수
     */
    public void incrementScore(
        String key,
        Long accommodationId
    ) {
        stringRedisTemplate.execute(
            INCREMENT_SCORE_SCRIPT,
            List.of(key),
            VIEW_SCORE,
            accommodationId.toString(),
            KEY_TTL_SECONDS
        );
    }

    /**
     * 일간 인기 숙소 랭킹의 지정 범위 ID를 점수 내림차순으로 조회합니다.
     *
     * start와 end는 Redis reverseRange의 inclusive 인덱스입니다.
     * 동일한 점수에서는 Redis의 역방향 사전순 정렬을 따릅니다.
     */
    public List<Long> findRankedAccommodationIds(
        String key,
        long start,
        long end
    ) {
        Set<String> accommodationIds =
            stringRedisTemplate.opsForZSet()
                .reverseRange(
                    key,
                    start,
                    end
                );

        if (
            accommodationIds == null
                || accommodationIds.isEmpty()
        ) {
            return List.of();
        }

        return accommodationIds.stream()
            .map(Long::valueOf)
            .toList();
    }
}
