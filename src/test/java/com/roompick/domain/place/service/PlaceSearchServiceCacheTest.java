package com.roompick.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.roompick.domain.place.client.PlaceSearchClient;
import com.roompick.domain.place.model.PlaceSearchCandidate;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 장소 검색의 정규화 기반 단기 캐시 정책을 검증합니다.
 */
@SpringJUnitConfig(PlaceSearchServiceCacheTest.CacheTestConfig.class)
class PlaceSearchServiceCacheTest {

    @Autowired
    private PlaceSearchService placeSearchService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private PlaceSearchClient placeSearchClient;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("placeSearches").clear();
    }

    @Test
    @DisplayName("동일하게 정규화된 query와 limit은 Client를 한 번만 호출한다")
    void cacheSameNormalizedQueryAndLimit() {
        given(placeSearchClient.search("강남역", 5))
            .willReturn(List.of(createCandidate()));

        List<?> first = placeSearchService.searchPlaces("  강남역  ", 5);
        List<?> second = placeSearchService.searchPlaces("강남역", 5);

        assertThat(second).isEqualTo(first);
        then(placeSearchClient)
            .should(times(1))
            .search("강남역", 5);
    }

    @Test
    @DisplayName("동일 query라도 limit이 다르면 별도로 Client를 호출한다")
    void useSeparateCacheEntriesByLimit() {
        given(placeSearchClient.search("강남역", 5))
            .willReturn(List.of(createCandidate()));
        given(placeSearchClient.search("강남역", 10))
            .willReturn(List.of(createCandidate()));

        placeSearchService.searchPlaces("강남역", 5);
        placeSearchService.searchPlaces("강남역", 10);

        then(placeSearchClient).should().search("강남역", 5);
        then(placeSearchClient).should().search("강남역", 10);
    }

    @Test
    @DisplayName("외부 API 실패는 캐시하지 않고 다음 요청에서 다시 호출한다")
    void doNotCacheClientFailure() {
        given(placeSearchClient.search("강남역", 5))
            .willThrow(
                new BusinessException(ErrorCode.PLACE_API_UNAVAILABLE)
            )
            .willReturn(List.of(createCandidate()));

        assertThatThrownBy(
            () -> placeSearchService.searchPlaces("강남역", 5)
        ).isInstanceOf(BusinessException.class);

        assertThat(placeSearchService.searchPlaces("강남역", 5))
            .hasSize(1);

        then(placeSearchClient)
            .should(times(2))
            .search("강남역", 5);
    }

    private PlaceSearchCandidate createCandidate() {
        return new PlaceSearchCandidate(
            "123456",
            "강남역 2호선",
            "서울 강남구 역삼동",
            "서울 강남구 강남대로 396",
            37.4979,
            127.0276,
            "교통,수송 > 지하철"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(PlaceSearchService.class)
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("placeSearches");
        }
    }
}
