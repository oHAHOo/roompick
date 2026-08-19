package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.exception.PopularAccommodationRankingUnavailableException;
import com.roompick.domain.accommodation.facade.AccommodationFacade;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.testsupport.SharedRedisTestContainer;

/**
 * 실제 Redis 응답 캐시와 Mock Service를 조합해 Single Flight를 검증합니다.
 *
 * Redis 응답 캐시의 Cold cache/HIT 동작은 실제 Redis를 사용합니다.
 * 랭킹과 숙소 Service는 동시 진입을 결정적으로 제어하고 역할 호출 횟수를
 * 검증하기 위한 Mock이며, 실제 Redis Sorted Set 명령과 MySQL SELECT 횟수로
 * 해석하지 않습니다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class PopularAccommodationCacheStampedeIntegrationTest {

    private static final PopularAccommodationPeriod DAILY =
        PopularAccommodationPeriod.DAILY;

    private static final String CACHE_NAME = "popularAccommodations";

    private static final int THREAD_COUNT = 10;

    private static final int LIMIT = 1;

    private static final long TEST_TIMEOUT_SECONDS = 5L;

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", SharedRedisTestContainer::host);
        registry.add("spring.data.redis.port", SharedRedisTestContainer::port);
        registry.add(
            "roompick.cache.popular-accommodations.single-flight.wait-timeout",
            () -> "2s"
        );
    }

    @Autowired
    private AccommodationFacade accommodationFacade;

    @Autowired
    private PopularAccommodationSingleFlightService singleFlightService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PopularAccommodationKeyGenerator keyGenerator;

    @MockitoBean
    private PopularAccommodationRankingService rankingService;

    @MockitoBean
    private AccommodationService accommodationService;

    private Cache popularAccommodationCache;

    @BeforeEach
    void setUp() {
        popularAccommodationCache = Objects.requireNonNull(
            cacheManager.getCache(CACHE_NAME)
        );
        popularAccommodationCache.clear();
    }

    @AfterEach
    void tearDown() {
        popularAccommodationCache.clear();
    }

    @Test
    void 동일한_Cold_cache_요청은_하나의_Service_조회_결과를_공유한다()
        throws Exception {

        List<Long> rankedIds = List.of(1L);
        AccommodationListResponseDto accommodation =
            new AccommodationListResponseDto(1L, "룸픽 호텔", "서울");
        CountDownLatch lookupEntered = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);

        given(rankingService.findRankedAccommodationIds(
            DAILY,
            LIMIT,
            0L,
            4L
        )).willAnswer(invocation -> {
            lookupEntered.countDown();
            await(releaseLookup);
            return rankedIds;
        });
        given(accommodationService.findAllActiveSummaryByIds(rankedIds))
            .willReturn(List.of(accommodation));

        List<List<PopularAccommodationResponseDto>> results =
            executeConcurrentRequests(lookupEntered, releaseLookup);

        for (List<PopularAccommodationResponseDto> result : results) {
            assertThat(result)
                .extracting(PopularAccommodationResponseDto::accommodationId)
                .containsExactly(1L);
        }
        then(rankingService).should(times(1))
            .findRankedAccommodationIds(DAILY, LIMIT, 0L, 4L);
        then(accommodationService).should(times(1))
            .findAllActiveSummaryByIds(rankedIds);
        assertThat(popularAccommodationCache.get(createCacheKey()))
            .isNotNull();

        popularAccommodationCache.clear();
        accommodationFacade.getPopularAccommodations(DAILY, LIMIT);
        then(rankingService).should(times(2))
            .findRankedAccommodationIds(DAILY, LIMIT, 0L, 4L);
    }

    @Test
    void Redis_랭킹_장애_fallback도_동일_Key_요청이_한_번만_실행한다()
        throws Exception {

        CountDownLatch lookupEntered = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        PopularAccommodationRankingUnavailableException unavailable =
            new PopularAccommodationRankingUnavailableException(
                new DataAccessResourceFailureException("Redis 연결 실패")
            );
        AccommodationListResponseDto latest =
            new AccommodationListResponseDto(7L, "최신 숙소", "부산");

        given(rankingService.findRankedAccommodationIds(
            DAILY,
            LIMIT,
            0L,
            4L
        )).willAnswer(invocation -> {
            lookupEntered.countDown();
            await(releaseLookup);
            throw unavailable;
        });
        given(accommodationService.findLatestActive(LIMIT))
            .willReturn(List.of(latest));

        List<List<PopularAccommodationResponseDto>> results =
            executeConcurrentRequests(lookupEntered, releaseLookup);

        for (List<PopularAccommodationResponseDto> result : results) {
            assertThat(result)
                .extracting(PopularAccommodationResponseDto::accommodationId)
                .containsExactly(7L);
            assertThat(result)
                .extracting(PopularAccommodationResponseDto::rank)
                .containsExactly(1);
        }
        then(rankingService).should(times(1))
            .findRankedAccommodationIds(DAILY, LIMIT, 0L, 4L);
        then(accommodationService).should(times(1))
            .findLatestActive(LIMIT);
        assertThat(popularAccommodationCache.get(createCacheKey()))
            .isNull();

        accommodationFacade.getPopularAccommodations(DAILY, LIMIT);
        then(rankingService).should(times(2))
            .findRankedAccommodationIds(DAILY, LIMIT, 0L, 4L);
        then(accommodationService).should(times(2))
            .findLatestActive(LIMIT);
    }

    private List<List<PopularAccommodationResponseDto>>
    executeConcurrentRequests(
        CountDownLatch lookupEntered,
        CountDownLatch releaseLookup
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<PopularAccommodationResponseDto>>> futures =
            new ArrayList<>();

        try {
            for (int index = 0; index < THREAD_COUNT; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return accommodationFacade.getPopularAccommodations(
                        DAILY,
                        LIMIT
                    );
                }));
            }
            assertThat(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
            start.countDown();
            assertThat(
                lookupEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            ).isTrue();
            awaitWaitingRequestCount();
            releaseLookup.countDown();

            List<List<PopularAccommodationResponseDto>> results =
                new ArrayList<>();
            for (Future<List<PopularAccommodationResponseDto>> future
                : futures) {
                results.add(future.get(
                    TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                ));
            }
            return results;
        } finally {
            start.countDown();
            releaseLookup.countDown();
            executor.shutdownNow();
            executor.awaitTermination(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        }
    }

    private void awaitWaitingRequestCount() {
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);

        while (
            singleFlightService.getWaitingRequestCount()
                < THREAD_COUNT - 1
        ) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("동시 요청이 제한 시간 안에 시작되지 않았습니다.");
            }
            Thread.onSpinWait();
        }
    }

    private String createCacheKey() {
        return keyGenerator.generateCurrentKey(DAILY) + ":" + LIMIT;
    }

    private static void await(CountDownLatch latch) {
        try {
            boolean completed = latch.await(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
            if (!completed) {
                throw new AssertionError("테스트 동기화 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("테스트 동기화 대기가 중단되었습니다.", exception);
        }
    }
}
