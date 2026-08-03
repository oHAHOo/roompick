package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Objects;
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

/**
 * 인기 숙소 조회 결과의 Redis 캐시 동작을
 * 실제 Redis 환경에서 검증합니다.
 *
 * 캐시 MISS와 HIT뿐 아니라,
 * TTL 만료 후 데이터가 다시 조회되는지도 확인합니다.
 */
@Tag("integration")
@Testcontainers
/*
 * 이 테스트 클래스에서만 인기 숙소 캐시 TTL을 1초로 설정합니다.
 *
 * 운영 기본값은 60초로 유지하면서,
 * 통합 테스트에서는 긴 대기 없이 실제 만료 동작을 검증합니다.
 */
@SpringBootTest(
    properties = {
        "roompick.cache.popular-accommodations-ttl=1s"
    }
)
@ActiveProfiles("test")
class PopularAccommodationQueryCacheIntegrationTest {

    private static final String CACHE_NAME =
        "popularAccommodations";

    private static final int REDIS_PORT = 6379;

    /**
     * 테스트에서 사용할 실제 Redis 컨테이너입니다.
     */
    @Container
    static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine")
        )
            .withExposedPorts(REDIS_PORT);

    /**
     * Testcontainers가 실행한 Redis의 접속 정보를
     * Spring Redis 설정에 동적으로 주입합니다.
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
     * @Cacheable이 적용된 실제 Spring Bean입니다.
     */
    @Autowired
    private PopularAccommodationQueryService
        popularAccommodationQueryService;

    /**
     * 테스트에서 Redis 캐시를 직접 조회하고
     * 초기화하기 위해 사용합니다.
     */
    @Autowired
    private CacheManager cacheManager;

    /**
     * 실제 @Cacheable Key와 동일한 Key를 생성하기 위해
     * 운영 코드의 Key 생성 Bean을 사용합니다.
     */
    @Autowired
    private PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    /**
     * 실제 Redis 랭킹 조회 대신
     * 호출 횟수를 검증할 Mock Bean입니다.
     */
    @MockitoBean
    private PopularAccommodationRankingService
        popularAccommodationRankingService;

    /**
     * 실제 DB 조회 대신
     * 호출 횟수를 검증할 Mock Bean입니다.
     */
    @MockitoBean
    private AccommodationService accommodationService;

    private Cache popularAccommodationCache;

    /**
     * 각 테스트를 시작하기 전에 인기 숙소 캐시를 가져오고
     * 이전 테스트의 캐시 데이터를 모두 삭제합니다.
     */
    @BeforeEach
    void setUp() {
        popularAccommodationCache =
            Objects.requireNonNull(
                cacheManager.getCache(CACHE_NAME)
            );

        popularAccommodationCache.clear();
    }

    /**
     * 다음 테스트에 영향을 주지 않도록
     * 테스트 종료 후 캐시를 다시 비웁니다.
     */
    @AfterEach
    void tearDown() {
        popularAccommodationCache.clear();
    }

    @Test
    @DisplayName(
        "동일한 인기 숙소 조회를 두 번 요청하면 "
            + "두 번째 요청은 Redis 캐시를 사용한다"
    )
    void useCacheOnSecondIdenticalRequest() {
        // given: Redis 랭킹에 포함된 숙소 ID를 준비합니다.
        int limit = 2;

        List<Long> rankedAccommodationIds =
            List.of(
                2L,
                1L
            );

        /*
         * DB 반환 순서는 Redis 인기 순서와 다르게 구성합니다.
         *
         * QueryService가 Redis 순서대로 응답을
         * 다시 조합하는지도 함께 확인할 수 있습니다.
         */
        AccommodationListResponseDto firstAccommodation =
            new AccommodationListResponseDto(
                2L,
                "두 번째 룸픽 호텔",
                "서울특별시 송파구"
            );

        AccommodationListResponseDto secondAccommodation =
            new AccommodationListResponseDto(
                1L,
                "첫 번째 룸픽 호텔",
                "서울특별시 강남구"
            );

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 9L)
        ).willReturn(
            rankedAccommodationIds
        );

        given(
            accommodationService
                .findAllActiveSummaryByIds(
                    rankedAccommodationIds
                )
        ).willReturn(
            List.of(
                secondAccommodation,
                firstAccommodation
            )
        );

        // when: 첫 요청은 캐시 MISS로 내부 조회를 실행합니다.
        List<PopularAccommodationResponseDto> firstResult =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        /*
         * 동일한 날짜와 limit으로 다시 요청합니다.
         *
         * 동일한 캐시 Key가 사용되므로
         * 두 번째 요청은 Redis 캐시 HIT가 되어야 합니다.
         */
        List<PopularAccommodationResponseDto> secondResult =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then: 첫 요청에서 인기 숙소 두 건이 반환됩니다.
        assertThat(firstResult)
            .hasSize(2);

        /*
         * 두 번째 요청은 Redis에 저장된 결과를 사용하므로
         * 첫 번째 응답과 내용이 같아야 합니다.
         */
        assertThat(secondResult)
            .isEqualTo(firstResult);

        /*
         * Redis 랭킹 조회는 캐시 MISS였던
         * 첫 번째 요청에서만 실행되어야 합니다.
         */
        then(popularAccommodationRankingService)
            .should(times(1))
            .findRankedAccommodationIds(limit, 0L, 9L);

        /*
         * DB 조회 역할의 Service도 캐시 MISS였던
         * 첫 번째 요청에서만 실행되어야 합니다.
         */
        then(accommodationService)
            .should(times(1))
            .findAllActiveSummaryByIds(
                rankedAccommodationIds
            );
    }

    @Test
    @DisplayName(
        "인기 숙소 캐시 TTL이 만료되면 "
            + "다음 요청에서 데이터를 다시 조회한다"
    )
    void reloadDataAfterCacheExpiration()
        throws InterruptedException {

        // given: Redis 랭킹과 DB 조회 결과를 준비합니다.
        int limit = 1;

        List<Long> rankedAccommodationIds =
            List.of(1L);

        AccommodationListResponseDto accommodation =
            new AccommodationListResponseDto(
                1L,
                "룸픽 호텔",
                "서울특별시 강남구"
            );

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 4L)
        ).willReturn(
            rankedAccommodationIds
        );

        given(
            accommodationService
                .findAllActiveSummaryByIds(
                    rankedAccommodationIds
                )
        ).willReturn(
            List.of(accommodation)
        );

        /*
         * 실제 @Cacheable에서 사용하는 Key와
         * 동일한 형식의 Key를 생성합니다.
         *
         * 날짜별 인기 랭킹 Key 뒤에
         * 요청한 limit 값이 포함됩니다.
         */
        String cacheKey =
            popularAccommodationKeyGenerator
                .generateTodayKey()
                + ":"
                + limit;

        // when: 첫 요청으로 인기 숙소 캐시를 생성합니다.
        List<PopularAccommodationResponseDto> firstResult =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then: 첫 번째 요청 결과가 정상적으로 반환됩니다.
        assertThat(firstResult)
            .hasSize(1);

        /*
         * 첫 요청이 끝난 직후에는
         * 실제 Redis 캐시에 데이터가 존재해야 합니다.
         */
        assertThat(
            popularAccommodationCache.get(cacheKey)
        ).isNotNull();

        /*
         * 테스트에서 설정한 TTL은 1초입니다.
         *
         * 실행 환경의 속도 차이를 고려해
         * Redis Key가 실제로 사라질 때까지 최대 5초 기다립니다.
         */
        waitUntilCacheExpires(cacheKey);

        // then: TTL이 지나면 캐시 데이터가 사라져야 합니다.
        assertThat(
            popularAccommodationCache.get(cacheKey)
        ).isNull();

        /*
         * 캐시 만료 후 동일한 요청을 다시 실행합니다.
         *
         * 캐시가 존재하지 않으므로 내부 조회 로직이
         * 다시 실행되어야 합니다.
         */
        List<PopularAccommodationResponseDto> secondResult =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then: 재조회 후에도 응답 내용은 동일해야 합니다.
        assertThat(secondResult)
            .isEqualTo(firstResult);

        /*
         * 첫 요청과 TTL 만료 후 요청에서 각각 한 번씩,
         * Redis 랭킹 조회가 총 두 번 실행되어야 합니다.
         */
        then(popularAccommodationRankingService)
            .should(times(2))
            .findRankedAccommodationIds(limit, 0L, 4L);

        /*
         * DB 조회 역할의 Service도 첫 요청과
         * TTL 만료 후 요청에서 총 두 번 실행되어야 합니다.
         */
        then(accommodationService)
            .should(times(2))
            .findAllActiveSummaryByIds(
                rankedAccommodationIds
            );
    }

    /**
     * Redis에서 지정한 인기 숙소 캐시 Key가
     * 실제로 만료될 때까지 기다립니다.
     *
     * 테스트 환경의 속도 차이를 고려해 최대 5초까지만 확인하고,
     * 제한 시간 안에 만료되지 않으면 테스트를 실패시킵니다.
     */
    private void waitUntilCacheExpires(
        String cacheKey
    ) throws InterruptedException {

        long timeoutAt =
            System.nanoTime()
                + TimeUnit.SECONDS.toNanos(5);

        while (
            popularAccommodationCache.get(cacheKey)
                != null
        ) {
            if (System.nanoTime() >= timeoutAt) {
                throw new AssertionError(
                    "인기 숙소 캐시가 제한 시간 안에 "
                        + "만료되지 않았습니다."
                );
            }

            /*
             * Redis를 지나치게 자주 조회하지 않도록
             * 100밀리초 간격으로 만료 여부를 확인합니다.
             */
            Thread.sleep(100);
        }
    }
}
