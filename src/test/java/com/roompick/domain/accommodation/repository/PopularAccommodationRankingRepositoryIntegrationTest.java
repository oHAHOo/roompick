package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis 컨테이너를 사용하여
 * 인기 숙소 Sorted Set 점수와 TTL 설정을 검증합니다.
 */
@Testcontainers
@DataRedisTest
@Import(PopularAccommodationRankingRepository.class)
class PopularAccommodationRankingRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final String DAILY_KEY =
        "roompick:popular:accommodations:daily:2026-07-29";

    /**
     * 테스트마다 독립적인 Redis 7 컨테이너를 사용합니다.
     *
     * 로컬에서 실행 중인 roompick-redis의 6379 포트와 겹치지 않도록
     * Testcontainers가 임의의 호스트 포트를 자동으로 할당합니다.
     */
    @Container
    private static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(
            DockerImageName.parse("redis:7")
        ).withExposedPorts(
            REDIS_PORT
        );

    @Autowired
    private PopularAccommodationRankingRepository
        popularAccommodationRankingRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Spring Data Redis가 Testcontainers의 임의 포트로 연결되도록 설정합니다.
     */
    @DynamicPropertySource
    static void configureRedis(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.data.redis.host",
            REDIS_CONTAINER::getHost
        );

        registry.add(
            "spring.data.redis.port",
            () -> REDIS_CONTAINER.getMappedPort(
                REDIS_PORT
            )
        );
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(
            DAILY_KEY
        );
    }

    @Test
    void 같은_숙소의_조회_점수를_누적하고_TTL을_설정한다() {
        // given
        Long accommodationId = 1L;

        // when
        popularAccommodationRankingRepository.incrementScore(
            DAILY_KEY,
            accommodationId
        );

        popularAccommodationRankingRepository.incrementScore(
            DAILY_KEY,
            accommodationId
        );

        // then
        Double score = stringRedisTemplate
            .opsForZSet()
            .score(
                DAILY_KEY,
                accommodationId.toString()
            );

        Long ttlSeconds = stringRedisTemplate.getExpire(
            DAILY_KEY,
            TimeUnit.SECONDS
        );

        assertThat(score).isEqualTo(
            2.0
        );

        assertThat(ttlSeconds)
            .isPositive()
            .isLessThanOrEqualTo(
                172800L
            );
    }

    @Test
    void 인기_숙소_ID를_지정_범위에서_점수_내림차순으로_조회한다() {
        // given
        Long firstAccommodationId = 1L;
        Long secondAccommodationId = 2L;
        Long thirdAccommodationId = 3L;

        popularAccommodationRankingRepository.incrementScore(
            DAILY_KEY,
            firstAccommodationId
        );

        for (int count = 0; count < 3; count++) {
            popularAccommodationRankingRepository.incrementScore(
                DAILY_KEY,
                secondAccommodationId
            );
        }

        for (int count = 0; count < 2; count++) {
            popularAccommodationRankingRepository.incrementScore(
                DAILY_KEY,
                thirdAccommodationId
            );
        }

        // when
        List<Long> result =
            popularAccommodationRankingRepository
                .findRankedAccommodationIds(
                    DAILY_KEY,
                    0L,
                    1L
                );

        // then
        assertThat(result).containsExactly(
            secondAccommodationId,
            thirdAccommodationId
        );
    }

    @Test
    void 점수가_같은_숙소는_Redis_역방향_사전순으로_조회한다() {
        // given
        Long firstAccommodationId = 1L;
        Long secondAccommodationId = 2L;
        Long thirdAccommodationId = 3L;

        popularAccommodationRankingRepository.incrementScore(
            DAILY_KEY,
            firstAccommodationId
        );

        popularAccommodationRankingRepository.incrementScore(
            DAILY_KEY,
            secondAccommodationId
        );

        popularAccommodationRankingRepository.incrementScore(
            DAILY_KEY,
            thirdAccommodationId
        );

        // when
        List<Long> result =
            popularAccommodationRankingRepository
                .findRankedAccommodationIds(
                    DAILY_KEY,
                    0L,
                    2L
                );

        // then
        assertThat(result).containsExactly(
            thirdAccommodationId,
            secondAccommodationId,
            firstAccommodationId
        );
    }

    @Test
    void 인기_숙소_랭킹이_없으면_빈_목록을_반환한다() {
        // when
        List<Long> result =
            popularAccommodationRankingRepository
                .findRankedAccommodationIds(
                    DAILY_KEY,
                    0L,
                    99L
                );

        // then
        assertThat(result).isEmpty();
    }
}
