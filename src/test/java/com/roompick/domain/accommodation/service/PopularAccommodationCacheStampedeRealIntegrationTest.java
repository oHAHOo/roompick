package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.facade.AccommodationFacade;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.accommodation.repository.PopularAccommodationRankingRepository;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.testsupport.SharedMySqlTestContainer;
import com.roompick.testsupport.SharedRedisTestContainer;

import jakarta.persistence.EntityManagerFactory;

/**
 * 실제 Redis Sorted Set과 MySQL 데이터를 사용해 Cold cache 동시 요청의
 * 랭킹 Repository 실행 횟수와 Hibernate SELECT 횟수를 검증합니다.
 */
@Tag("integration")
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.hikari.maximum-pool-size=5",
        "roompick.cache.popular-accommodations.single-flight.wait-timeout=3s"
    }
)
@ActiveProfiles("test")
class PopularAccommodationCacheStampedeRealIntegrationTest {

    private static final String DATABASE_NAME = "roompick_stampede_test";

    private static final int REQUEST_COUNT = 10;

    private static final int LIMIT = 10;

    private static final long TEST_TIMEOUT_SECONDS = 10L;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.createDatabaseIfAbsent(DATABASE_NAME);
        registry.add(
            "spring.datasource.url",
            () -> SharedMySqlTestContainer.jdbcUrl(DATABASE_NAME)
        );
        registry.add(
            "spring.datasource.username",
            () -> SharedMySqlTestContainer.USERNAME
        );
        registry.add(
            "spring.datasource.password",
            () -> SharedMySqlTestContainer.PASSWORD
        );
        registry.add(
            "spring.datasource.driver-class-name",
            () -> "com.mysql.cj.jdbc.Driver"
        );
        registry.add("spring.data.redis.host", SharedRedisTestContainer::host);
        registry.add("spring.data.redis.port", SharedRedisTestContainer::port);
    }

    @Autowired
    private AccommodationFacade accommodationFacade;

    @Autowired
    private PopularAccommodationSingleFlightService singleFlightService;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private PopularAccommodationKeyGenerator keyGenerator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoSpyBean
    private PopularAccommodationRankingRepository rankingRepository;

    private Cache responseCache;

    private Statistics statistics;

    private String rankingKey;

    @BeforeEach
    void setUp() {
        responseCache = Objects.requireNonNull(
            cacheManager.getCache("popularAccommodations")
        );
        responseCache.clear();
        rankingKey = keyGenerator.generateCurrentKey(
            PopularAccommodationPeriod.DAILY
        );
        redisTemplate.delete(rankingKey);
        accommodationRepository.deleteAll();

        Accommodation accommodation = accommodationRepository.saveAndFlush(
            Accommodation.create(
                "실제 Stampede 숙소",
                "서울특별시",
                "실제 Redis와 MySQL 검증",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            )
        );
        redisTemplate.opsForZSet().add(
            rankingKey,
            accommodation.getId().toString(),
            100.0
        );

        statistics = entityManagerFactory.unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
        clearInvocations(rankingRepository);
    }

    @AfterEach
    void tearDown() {
        responseCache.clear();
        redisTemplate.delete(rankingKey);
        accommodationRepository.deleteAll();
    }

    @Test
    void 실제_Redis와_MySQL_Cold_cache_동시_요청은_원본을_한_번_조회한다()
        throws Exception {

        CountDownLatch repositoryEntered = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        doAnswer(invocation -> {
            repositoryEntered.countDown();
            await(releaseRepository);
            return invocation.callRealMethod();
        }).when(rankingRepository).findRankedAccommodationIds(
            rankingKey,
            0L,
            49L
        );

        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<PopularAccommodationResponseDto>>> futures =
            new ArrayList<>();

        try {
            for (int index = 0; index < REQUEST_COUNT; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return accommodationFacade.getPopularAccommodations(
                        PopularAccommodationPeriod.DAILY,
                        LIMIT
                    );
                }));
            }
            assertThat(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
            start.countDown();
            assertThat(
                repositoryEntered.await(
                    TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            ).isTrue();
            awaitWaitingRequests();
            releaseRepository.countDown();

            for (Future<List<PopularAccommodationResponseDto>> future
                : futures) {
                assertThat(future.get(
                    TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )).extracting(
                    PopularAccommodationResponseDto::name
                ).containsExactly("실제 Stampede 숙소");
            }
        } finally {
            start.countDown();
            releaseRepository.countDown();
            executor.shutdownNow();
            executor.awaitTermination(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        }

        verify(rankingRepository, times(1))
            .findRankedAccommodationIds(rankingKey, 0L, 49L);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(responseCache.get(rankingKey + ":" + LIMIT))
            .isNotNull();
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

    private void awaitWaitingRequests() {
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);

        while (
            singleFlightService.getWaitingRequestCount()
                < REQUEST_COUNT - 1
        ) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                    "대기 요청이 제한 시간 안에 Single Flight에 진입하지 않았습니다."
                );
            }
            Thread.onSpinWait();
        }
    }
}
