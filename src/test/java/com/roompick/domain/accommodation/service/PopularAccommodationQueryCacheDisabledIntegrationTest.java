package com.roompick.domain.accommodation.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

/**
 * 인기 숙소 응답 캐시 비활성화 설정이 랭킹 조회에는 영향을 주지 않는지 검증합니다.
 *
 * {@code roompick.cache.popular-accommodations-enabled=false} 설정으로
 * {@code @Cacheable} condition은 false가 됩니다. condition은 캐시 Key 생성과
 * Redis 캐시 접근보다 먼저 평가되므로 이 테스트는 Redis에 접근하지 않으며,
 * 별도의 Redis Testcontainers가 필요하지 않습니다.
 */
@Tag("integration")
@SpringBootTest(
    properties = {
        "roompick.cache.popular-accommodations-enabled=false"
    }
)
@ActiveProfiles("test")
class PopularAccommodationQueryCacheDisabledIntegrationTest {

    @Autowired
    private PopularAccommodationQueryService
        popularAccommodationQueryService;

    @MockitoBean
    private PopularAccommodationRankingService
        popularAccommodationRankingService;

    @MockitoBean
    private AccommodationService accommodationService;

    @Test
    void 캐시를_비활성화하면_동일_요청도_랭킹을_다시_조회한다() {
        int limit = 10;

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(
                    PopularAccommodationPeriod.DAILY,
                    limit,
                    0L,
                    49L
                )
        ).willReturn(List.of());

        popularAccommodationQueryService.getPopularAccommodations(
            PopularAccommodationPeriod.DAILY,
            limit
        );
        popularAccommodationQueryService.getPopularAccommodations(
            PopularAccommodationPeriod.DAILY,
            limit
        );

        then(popularAccommodationRankingService).should(times(2))
            .findRankedAccommodationIds(
                PopularAccommodationPeriod.DAILY,
                limit,
                0L,
                49L
            );
        then(accommodationService).shouldHaveNoInteractions();
    }
}
