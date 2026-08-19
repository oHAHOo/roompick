package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.testsupport.SharedRedisTestContainer;

/**
 * 누적 인기 숙소 랭킹과 기간별 DAILY/WEEKLY 랭킹의
 * 최신성 차이를 실제 Redis Sorted Set으로 비교합니다.
 *
 * 누적 랭킹은 제품 발전 과정의 V1 재현 시나리오이며,
 * 현재 운영 기능인 DAILY/WEEKLY 랭킹과 분리된 테스트 전용 키를 사용합니다.
 */
@DataRedisTest
@Import(PopularAccommodationRankingRepository.class)
class PopularAccommodationRankingFreshnessIntegrationTest {

    /**
     * 운영 Redis 키와 충돌하지 않는 V1 재현 전용 누적 랭킹 키입니다.
     *
     * 누적 랭킹의 장기 고착 문제를 재현하기 위해 TTL을 설정하지 않습니다.
     */
    private static final String CUMULATIVE_KEY =
        "roompick:test:popular:accommodations:cumulative";

    private static final String CUMULATIVE_RESPONSE_CACHE_KEY =
        "popularAccommodations::"
            + "roompick:test:popular:accommodations:cumulative:3";

    @Autowired
    private PopularAccommodationRankingRepository
        popularAccommodationRankingRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 각 테스트에서 생성한 Redis 키만 정리하기 위해 관리합니다.
     */
    private final Set<String> createdKeys =
        new HashSet<>();

    private final PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator =
        new PopularAccommodationKeyGenerator(
            Clock.systemUTC()
        );

    @DynamicPropertySource
    static void configureRedis(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.data.redis.host",
            SharedRedisTestContainer::host
        );

        registry.add(
            "spring.data.redis.port",
            SharedRedisTestContainer::port
        );
    }

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(
            createdKeys
        );
    }

    @Test
    void 누적_랭킹에서는_과거_인기_숙소가_남지만_DAILY에서는_최근_인기_숙소가_앞선다() {
        // given
        Long pastPopularAccommodationId = 1L;
        Long recentPopularAccommodationId = 2L;
        Long anotherAccommodationId = 3L;

        String dailyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.DAILY,
                LocalDate.of(
                    2026,
                    8,
                    4
                )
            );

        setScore(
            CUMULATIVE_KEY,
            pastPopularAccommodationId,
            1_000.0
        );

        setScore(
            CUMULATIVE_KEY,
            recentPopularAccommodationId,
            200.0
        );

        setScore(
            CUMULATIVE_KEY,
            anotherAccommodationId,
            100.0
        );

        setScore(
            dailyKey,
            pastPopularAccommodationId,
            1.0
        );

        setScore(
            dailyKey,
            recentPopularAccommodationId,
            50.0
        );

        setScore(
            dailyKey,
            anotherAccommodationId,
            20.0
        );

        // when
        List<Long> cumulativeRanking =
            findTopAccommodationIds(
                CUMULATIVE_KEY,
                3
            );

        List<Long> dailyRanking =
            findTopAccommodationIds(
                dailyKey,
                3
            );

        // then
        assertThat(cumulativeRanking).containsExactly(
            pastPopularAccommodationId,
            recentPopularAccommodationId,
            anotherAccommodationId
        );

        assertThat(dailyRanking).containsExactly(
            recentPopularAccommodationId,
            anotherAccommodationId,
            pastPopularAccommodationId
        );
    }

    @Test
    void 신규_숙소는_누적_TOP2에_진입하지_못하지만_기간별_랭킹에는_진입한다() {
        // given
        Long firstExistingAccommodationId = 1L;
        Long secondExistingAccommodationId = 2L;
        Long newAccommodationId = 3L;

        String dailyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.DAILY,
                LocalDate.of(
                    2026,
                    8,
                    4
                )
            );

        String weeklyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.WEEKLY,
                LocalDate.of(
                    2026,
                    8,
                    4
                )
            );

        setScore(
            CUMULATIVE_KEY,
            firstExistingAccommodationId,
            1_000.0
        );

        setScore(
            CUMULATIVE_KEY,
            secondExistingAccommodationId,
            900.0
        );

        setScore(
            CUMULATIVE_KEY,
            newAccommodationId,
            80.0
        );

        setScore(
            dailyKey,
            firstExistingAccommodationId,
            5.0
        );

        setScore(
            dailyKey,
            secondExistingAccommodationId,
            3.0
        );

        setScore(
            dailyKey,
            newAccommodationId,
            100.0
        );

        setScore(
            weeklyKey,
            firstExistingAccommodationId,
            30.0
        );

        setScore(
            weeklyKey,
            secondExistingAccommodationId,
            20.0
        );

        setScore(
            weeklyKey,
            newAccommodationId,
            150.0
        );

        // when
        List<Long> cumulativeTopTwo =
            findTopAccommodationIds(
                CUMULATIVE_KEY,
                2
            );

        List<Long> dailyTopTwo =
            findTopAccommodationIds(
                dailyKey,
                2
            );

        List<Long> weeklyTopTwo =
            findTopAccommodationIds(
                weeklyKey,
                2
            );

        // then
        assertThat(cumulativeTopTwo)
            .containsExactly(
                firstExistingAccommodationId,
                secondExistingAccommodationId
            );

        assertThat(dailyTopTwo)
            .startsWith(
                newAccommodationId
            );

        assertThat(weeklyTopTwo)
            .startsWith(
                newAccommodationId
            );
    }

    @Test
    void 응답_캐시를_삭제해도_누적_랭킹_원본과_TOP_N은_변하지_않는다() {
        // given
        Long firstAccommodationId = 1L;
        Long secondAccommodationId = 2L;
        Long thirdAccommodationId = 3L;

        setScore(
            CUMULATIVE_KEY,
            firstAccommodationId,
            1_000.0
        );

        setScore(
            CUMULATIVE_KEY,
            secondAccommodationId,
            800.0
        );

        setScore(
            CUMULATIVE_KEY,
            thirdAccommodationId,
            500.0
        );

        registerKey(
            CUMULATIVE_RESPONSE_CACHE_KEY
        );

        stringRedisTemplate.opsForValue().set(
            CUMULATIVE_RESPONSE_CACHE_KEY,
            "cached-response"
        );

        List<Long> rankingBeforeCacheEviction =
            findTopAccommodationIds(
                CUMULATIVE_KEY,
                3
            );

        // when
        stringRedisTemplate.delete(
            CUMULATIVE_RESPONSE_CACHE_KEY
        );

        List<Long> rankingAfterCacheEviction =
            findTopAccommodationIds(
                CUMULATIVE_KEY,
                3
            );

        // then
        assertThat(
            stringRedisTemplate.hasKey(
                CUMULATIVE_RESPONSE_CACHE_KEY
            )
        ).isFalse();

        assertThat(
            stringRedisTemplate.hasKey(
                CUMULATIVE_KEY
            )
        ).isTrue();

        assertThat(rankingAfterCacheEviction)
            .containsExactlyElementsOf(
                rankingBeforeCacheEviction
            );
    }

    @Test
    void 날짜와_주간_경계가_변경되면_이전_기간의_점수가_새_랭킹에_남지_않는다() {
        // given
        Long previousPopularAccommodationId = 1L;
        Long currentPopularAccommodationId = 2L;

        String previousDailyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.DAILY,
                LocalDate.of(
                    2026,
                    8,
                    4
                )
            );

        String currentDailyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.DAILY,
                LocalDate.of(
                    2026,
                    8,
                    5
                )
            );

        String previousWeeklyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.WEEKLY,
                LocalDate.of(
                    2026,
                    8,
                    2
                )
            );

        String currentWeeklyKey =
            popularAccommodationKeyGenerator.generateKey(
                PopularAccommodationPeriod.WEEKLY,
                LocalDate.of(
                    2026,
                    8,
                    3
                )
            );

        setScore(
            previousDailyKey,
            previousPopularAccommodationId,
            1_000.0
        );

        setScore(
            previousWeeklyKey,
            previousPopularAccommodationId,
            1_000.0
        );

        // when
        List<Long> emptyCurrentDailyRanking =
            findTopAccommodationIds(
                currentDailyKey,
                3
            );

        List<Long> emptyCurrentWeeklyRanking =
            findTopAccommodationIds(
                currentWeeklyKey,
                3
            );

        setScore(
            currentDailyKey,
            currentPopularAccommodationId,
            10.0
        );

        setScore(
            currentWeeklyKey,
            currentPopularAccommodationId,
            20.0
        );

        List<Long> currentDailyRanking =
            findTopAccommodationIds(
                currentDailyKey,
                3
            );

        List<Long> currentWeeklyRanking =
            findTopAccommodationIds(
                currentWeeklyKey,
                3
            );

        // then
        assertThat(previousDailyKey)
            .isNotEqualTo(
                currentDailyKey
            );

        assertThat(previousWeeklyKey)
            .isNotEqualTo(
                currentWeeklyKey
            );

        assertThat(emptyCurrentDailyRanking)
            .isEmpty();

        assertThat(emptyCurrentWeeklyRanking)
            .isEmpty();

        assertThat(currentDailyRanking)
            .containsExactly(
                currentPopularAccommodationId
            );

        assertThat(currentWeeklyRanking)
            .containsExactly(
                currentPopularAccommodationId
            );
    }

    /**
     * 이슈 재현을 위해 테스트 데이터를 Redis Sorted Set에 직접 구성합니다.
     *
     * 누적 랭킹에는 TTL을 적용하지 않으며,
     * 기간별 랭킹도 점수 비교 자체에 집중하기 위해 원하는 점수를 직접 입력합니다.
     */
    private void setScore(
        String key,
        Long accommodationId,
        double score
    ) {
        registerKey(
            key
        );

        stringRedisTemplate.opsForZSet().add(
            key,
            accommodationId.toString(),
            score
        );
    }

    private List<Long> findTopAccommodationIds(
        String key,
        int limit
    ) {
        return popularAccommodationRankingRepository
            .findRankedAccommodationIds(
                key,
                0L,
                limit - 1L
            );
    }

    private void registerKey(
        String key
    ) {
        createdKeys.add(
            key
        );
    }
}
