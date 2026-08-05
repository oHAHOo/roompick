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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

/**
 * 인기 숙소 응답 캐시가 비어 있는 상태에서
 * 동일한 캐시 Key의 동시 요청을 Single Flight가
 * 하나의 원본 조회로 묶는지 실제 Redis 환경에서 검증합니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PopularAccommodationCacheStampedeIntegrationTest {

    private static final PopularAccommodationPeriod DAILY =
        PopularAccommodationPeriod.DAILY;

    private static final String CACHE_NAME =
        "popularAccommodations";

    private static final int REDIS_PORT = 6379;

    private static final int THREAD_COUNT = 10;

    private static final int LIMIT = 1;

    private static final long WAIT_TIMEOUT_SECONDS = 5L;

    /**
     * 실제 Redis 캐시 동작을 검증하기 위한
     * Redis 7 Testcontainers 환경입니다.
     */
    @Container
    static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine")
        )
            .withExposedPorts(REDIS_PORT);

    /**
     * Testcontainers가 실행한 Redis 접속 정보를
     * Spring 테스트 환경에 동적으로 주입합니다.
     */
    @DynamicPropertySource
    static void registerRedisProperties(
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

    /**
     * 동일 캐시 Key의 동시 요청을 하나의 작업으로 묶는
     * 실제 Single Flight Service입니다.
     */
    @Autowired
    private PopularAccommodationSingleFlightService
        popularAccommodationSingleFlightService;

    /**
     * 테스트 전후 인기 숙소 응답 캐시를
     * 직접 초기화하기 위해 사용합니다.
     */
    @Autowired
    private CacheManager cacheManager;

    /**
     * 운영 코드와 동일한 캐시 Key를
     * 생성하기 위해 사용합니다.
     */
    @Autowired
    private PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    /**
     * 동시 요청에서 Redis 랭킹 원본 조회가
     * 몇 번 실행되는지 확인하기 위한 Mock Bean입니다.
     */
    @MockitoBean
    private PopularAccommodationRankingService
        popularAccommodationRankingService;

    /**
     * 동시 요청에서 MySQL 조회 역할이
     * 몇 번 실행되는지 확인하기 위한 Mock Bean입니다.
     */
    @MockitoBean
    private AccommodationService accommodationService;

    private Cache popularAccommodationCache;

    /**
     * 모든 테스트는 응답 캐시가 존재하지 않는
     * Cold cache 상태에서 시작합니다.
     */
    @BeforeEach
    void setUp() {
        popularAccommodationCache =
            Objects.requireNonNull(
                cacheManager.getCache(
                    CACHE_NAME
                )
            );

        popularAccommodationCache.clear();
    }

    /**
     * 테스트 결과가 다음 테스트에 영향을 주지 않도록
     * 종료 후에도 응답 캐시를 제거합니다.
     */
    @AfterEach
    void tearDown() {
        popularAccommodationCache.clear();
    }

    @Test
    @DisplayName(
        "Cold cache 상태의 동일한 동시 요청은 "
            + "하나의 원본 조회 결과를 공유한다"
    )
    void shareSingleOriginalLookupOnConcurrentCacheMiss()
        throws Exception {

        /*
         * given: 모든 요청이 동일한 캐시 Key를 사용하도록
         * 같은 기간과 limit 값을 사용합니다.
         */
        List<Long> rankedAccommodationIds =
            List.of(1L);

        AccommodationListResponseDto accommodation =
            new AccommodationListResponseDto(
                1L,
                "룸픽 호텔",
                "서울특별시 강남구"
            );

        /*
         * 각 작업 스레드가 실행 준비를 마쳤는지
         * 확인하기 위한 출발 준비 신호입니다.
         */
        CountDownLatch ready =
            new CountDownLatch(THREAD_COUNT);

        /*
         * 준비된 모든 요청을 같은 시점에
         * 출발시키기 위한 시작 신호입니다.
         */
        CountDownLatch start =
            new CountDownLatch(1);

        /*
         * 최초 요청이 Cache Miss 후 실제 랭킹 조회까지
         * 진입했는지 확인합니다.
         */
        CountDownLatch rankingLookupEntered =
            new CountDownLatch(1);

        /*
         * 최초 요청이 캐시를 생성하기 전에
         * 나머지 요청도 동일한 Single Flight 작업을
         * 확인할 수 있도록 원본 조회를 잠시 막습니다.
         */
        CountDownLatch releaseRankingLookup =
            new CountDownLatch(1);

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(
                    DAILY,
                    LIMIT,
                    0L,
                    4L
                )
        ).willAnswer(invocation -> {
            rankingLookupEntered.countDown();

            boolean released =
                releaseRankingLookup.await(
                    WAIT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            if (!released) {
                throw new AssertionError(
                    "랭킹 조회 대기 시간이 초과되었습니다."
                );
            }

            return rankedAccommodationIds;
        });

        given(
            accommodationService
                .findAllActiveSummaryByIds(
                    rankedAccommodationIds
                )
        ).willReturn(
            List.of(accommodation)
        );

        ExecutorService executorService =
            Executors.newFixedThreadPool(
                THREAD_COUNT
            );

        List<Future<List<PopularAccommodationResponseDto>>>
            futures = new ArrayList<>();

        try {
            /*
             * when: 동일한 인기 숙소 요청을 수행할 작업을
             * 스레드 수만큼 등록합니다.
             */
            for (int index = 0; index < THREAD_COUNT; index++) {
                Future<List<PopularAccommodationResponseDto>>
                    future = executorService.submit(() -> {

                    ready.countDown();

                    boolean started =
                        start.await(
                            WAIT_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                        );

                    if (!started) {
                        throw new AssertionError(
                            "동시 요청 시작 대기 시간이 "
                                + "초과되었습니다."
                        );
                    }

                    return popularAccommodationSingleFlightService
                        .getPopularAccommodations(
                            DAILY,
                            LIMIT
                        );
                });

                futures.add(future);
            }

            /*
             * 모든 스레드가 시작 신호를 기다리는 상태가
             * 될 때까지 기다립니다.
             */
            boolean allThreadsReady =
                ready.await(
                    WAIT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            assertThat(allThreadsReady)
                .as(
                    "모든 요청 스레드가 제한 시간 안에 "
                        + "준비되어야 합니다."
                )
                .isTrue();

            /*
             * 준비된 요청들을 한 번에 출발시킵니다.
             */
            start.countDown();

            /*
             * 최초 요청이 실제 원본 조회에 진입할 때까지
             * 결과 반환을 막아 Cold cache 동시 요청 조건을 유지합니다.
             */
            boolean firstRequestEnteredOriginalLookup =
                rankingLookupEntered.await(
                    WAIT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            assertThat(firstRequestEnteredOriginalLookup)
                .as(
                    "최초 요청이 제한 시간 안에 "
                        + "원본 조회에 진입해야 합니다."
                )
                .isTrue();

            /*
             * 최초 요청이 랭킹 결과를 반환하고
             * 캐시를 생성할 수 있도록 해제합니다.
             */
            releaseRankingLookup.countDown();

            /*
             * 모든 동시 요청이 예외 없이
             * 동일한 인기 숙소 결과를 반환하는지 확인합니다.
             */
            for (
                Future<List<PopularAccommodationResponseDto>>
                    future : futures
            ) {
                List<PopularAccommodationResponseDto> result =
                    future.get(
                        WAIT_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                    );

                assertThat(result)
                    .hasSize(1);

                assertThat(
                    result.get(0).accommodationId()
                ).isEqualTo(1L);
            }
        } finally {
            /*
             * 중간 검증이 실패하더라도 대기 중인 작업이
             * 영구적으로 남지 않도록 반드시 해제합니다.
             */
            start.countDown();
            releaseRankingLookup.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        }

        /*
         * then: 동일한 Cold cache Key의 동시 요청 10건이
         * 하나의 Redis 랭킹 원본 조회를 공유해야 합니다.
         */
        then(popularAccommodationRankingService)
            .should(times(1))
            .findRankedAccommodationIds(
                DAILY,
                LIMIT,
                0L,
                4L
            );

        /*
         * 랭킹 ID에 해당하는 MySQL 조회 역할도
         * 최초 요청 한 건에서만 실행되어야 합니다.
         */
        then(accommodationService)
            .should(times(1))
            .findAllActiveSummaryByIds(
                rankedAccommodationIds
            );

        /*
         * 최초 요청이 생성한 응답은
         * 최종적으로 Redis 캐시에 저장되어야 합니다.
         */
        assertThat(
            popularAccommodationCache.get(
                createCacheKey()
            )
        ).isNotNull();
    }

    /**
     * 실제 @Cacheable에서 사용하는 형식과 동일하게
     * 테스트 대상 캐시 Key를 생성합니다.
     */
    private String createCacheKey() {
        return popularAccommodationKeyGenerator
            .generateCurrentKey(DAILY)
            + ":"
            + LIMIT;
    }
}
