package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Objects;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.testsupport.SharedRedisTestContainer;

/**
 * 숙소 정보 변경 트랜잭션의 성공·롤백 여부에 따라
 * 인기 숙소 Redis 캐시가 올바르게 삭제되는지 검증합니다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class PopularAccommodationCacheEvictionTransactionIntegrationTest {

    private static final String CACHE_NAME =
        "popularAccommodations";

    @DynamicPropertySource
    static void registerRedisProperties(
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

    @Autowired
    private AccommodationService accommodationService;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Cache popularAccommodationCache;

    @BeforeEach
    void setUp() {
        popularAccommodationCache =
            Objects.requireNonNull(
                cacheManager.getCache(CACHE_NAME)
            );

        popularAccommodationCache.clear();
    }

    @AfterEach
    void tearDown() {
        popularAccommodationCache.clear();
        accommodationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "숙소 공개 정보 수정 트랜잭션이 커밋되면 "
            + "인기 숙소 캐시를 삭제한다"
    )
    void evictCacheAfterTransactionCommit() {
        // given
        Accommodation accommodation =
            saveAccommodation();

        String dailyCacheKey = "daily:commit-test-key";
        String weeklyCacheKey = "weekly:commit-test-key";

        putPeriodCaches(dailyCacheKey, weeklyCacheKey);

        assertThat(
            popularAccommodationCache.get(dailyCacheKey)
        ).isNotNull();

        // when
        accommodationService.updatePublicInformation(
            accommodation.getId(),
            "수정된 룸픽 호텔",
            "서울특별시 송파구",
            "수정된 숙소 설명",
            LocalTime.of(16, 0),
            LocalTime.of(10, 0)
        );

        // then
        assertThat(
            popularAccommodationCache.get(dailyCacheKey)
        ).isNull();
        assertThat(popularAccommodationCache.get(weeklyCacheKey))
            .isNull();
    }

    @Test
    @DisplayName(
        "숙소 공개 정보 수정 트랜잭션이 롤백되면 "
            + "인기 숙소 캐시를 유지한다"
    )
    void keepCacheAfterTransactionRollback() {
        // given
        Accommodation accommodation =
            saveAccommodation();

        String dailyCacheKey = "daily:rollback-test-key";
        String weeklyCacheKey = "weekly:rollback-test-key";

        putPeriodCaches(dailyCacheKey, weeklyCacheKey);

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        // when
        transactionTemplate.executeWithoutResult(
            transactionStatus -> {
                accommodationService
                    .updatePublicInformation(
                        accommodation.getId(),
                        "롤백될 룸픽 호텔",
                        "서울특별시 종로구",
                        "롤백될 숙소 설명",
                        LocalTime.of(17, 0),
                        LocalTime.of(9, 0)
                    );

                transactionStatus.setRollbackOnly();
            }
        );

        // then
        assertThat(
            popularAccommodationCache.get(dailyCacheKey)
        ).isNotNull();
        assertThat(popularAccommodationCache.get(weeklyCacheKey))
            .isNotNull();

        Accommodation savedAccommodation =
            accommodationRepository
                .findById(accommodation.getId())
                .orElseThrow();

        assertThat(savedAccommodation.getName())
            .isEqualTo("룸픽 호텔");
    }

    @Test
    @DisplayName(
        "숙소 비공개 전환 트랜잭션이 커밋되면 "
            + "인기 숙소 캐시를 삭제한다"
    )
    void evictCacheAfterInactivationCommit() {
        // given
        Accommodation accommodation =
            saveAccommodation();

        String dailyCacheKey = "daily:inactivation-commit-test-key";
        String weeklyCacheKey = "weekly:inactivation-commit-test-key";

        putPeriodCaches(dailyCacheKey, weeklyCacheKey);

        assertThat(
            popularAccommodationCache.get(dailyCacheKey)
        ).isNotNull();

        // when
        accommodationService.inactivateAccommodation(
            accommodation.getId()
        );

        // then
        assertThat(
            popularAccommodationCache.get(dailyCacheKey)
        ).isNull();
        assertThat(popularAccommodationCache.get(weeklyCacheKey))
            .isNull();
    }

    private void putPeriodCaches(
        String dailyCacheKey,
        String weeklyCacheKey
    ) {
        popularAccommodationCache.put(dailyCacheKey, "daily-value");
        popularAccommodationCache.put(weeklyCacheKey, "weekly-value");
    }

    /**
     * 트랜잭션 캐시 삭제 테스트에 사용할 숙소를 저장합니다.
     */
    private Accommodation saveAccommodation() {
        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 강남구",
                "캐시 트랜잭션 테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        return accommodationRepository.saveAndFlush(
            accommodation
        );
    }
}
