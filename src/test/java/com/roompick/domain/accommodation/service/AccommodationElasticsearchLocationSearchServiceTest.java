package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;
import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.repository.AccommodationElasticsearchLocationSearchRepository;
import com.roompick.domain.accommodation.repository.AccommodationElasticsearchLocationSearchResult;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * Elasticsearch 위치 기반 숙소 검색 Service 테스트입니다.
 *
 * 검색 조건 검증, keyword 정규화,
 * Elasticsearch 검색 결과의 응답 DTO 변환을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationElasticsearchLocationSearchServiceTest {

    @Mock
    private AccommodationElasticsearchLocationSearchRepository
        accommodationElasticsearchLocationSearchRepository;

    @InjectMocks
    private AccommodationElasticsearchLocationSearchService
        accommodationElasticsearchLocationSearchService;

    @Test
    void 위치_기반_숙소_검색에_성공한다() {
        // given
        String keyword = "룸픽";
        double latitude = 37.5665;
        double longitude = 126.9780;
        double radiusKm = 5.0;
        int limit = 20;

        AccommodationSearchDocument document =
            AccommodationSearchDocument.create(
                1L,
                "룸픽 서울 호텔",
                "서울특별시 중구",
                "ACTIVE",
                37.5658,
                126.9785,
                "https://images.roompick.example.com/accommodations/1.jpg"
            );

        AccommodationElasticsearchLocationSearchResult searchResult =
            new AccommodationElasticsearchLocationSearchResult(
                document,
                0.85
            );

        given(
            accommodationElasticsearchLocationSearchRepository.searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            )
        ).willReturn(
            List.of(searchResult)
        );

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationElasticsearchLocationSearchService.searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        // then
        assertThat(result).hasSize(1);

        AccommodationLocationSearchResponseDto response =
            result.get(0);

        assertThat(response.accommodationId())
            .isEqualTo(1L);

        assertThat(response.name())
            .isEqualTo("룸픽 서울 호텔");

        assertThat(response.address())
            .isEqualTo("서울특별시 중구");

        assertThat(response.latitude())
            .isEqualTo(37.5658);

        assertThat(response.longitude())
            .isEqualTo(126.9785);

        assertThat(response.distanceKm())
            .isEqualTo(0.85);

        assertThat(response.imageUrl())
            .isEqualTo("https://images.roompick.example.com/accommodations/1.jpg");
    }

    @Test
    void 검색어의_앞뒤_공백을_제거한다() {
        // given
        String keyword = "  룸픽  ";

        given(
            accommodationElasticsearchLocationSearchRepository.searchNearby(
                "룸픽",
                37.5665,
                126.9780,
                5.0,
                20
            )
        ).willReturn(
            List.of()
        );

        // when
        accommodationElasticsearchLocationSearchService.searchNearby(
            keyword,
            37.5665,
            126.9780,
            5.0,
            20
        );

        // then
        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should()
            .searchNearby(
                "룸픽",
                37.5665,
                126.9780,
                5.0,
                20
            );
    }

    @Test
    void 빈_검색어는_null로_변환한다() {
        // given
        given(
            accommodationElasticsearchLocationSearchRepository.searchNearby(
                null,
                37.5665,
                126.9780,
                5.0,
                20
            )
        ).willReturn(
            List.of()
        );

        // when
        accommodationElasticsearchLocationSearchService.searchNearby(
            "   ",
            37.5665,
            126.9780,
            5.0,
            20
        );

        // then
        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should()
            .searchNearby(
                null,
                37.5665,
                126.9780,
                5.0,
                20
            );
    }

    @Test
    void 위도가_허용_범위를_벗어나면_예외가_발생한다() {
        // given
        double invalidLatitude = 90.000001;

        // when & then
        assertThatThrownBy(
            () ->
                accommodationElasticsearchLocationSearchService
                    .searchNearby(
                        null,
                        invalidLatitude,
                        126.9780,
                        5.0,
                        20
                    )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.ACCOMMODATION_LATITUDE_OUT_OF_RANGE
            );

        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should(never())
            .searchNearby(
                null,
                invalidLatitude,
                126.9780,
                5.0,
                20
            );
    }

    @Test
    void 경도가_허용_범위를_벗어나면_예외가_발생한다() {
        // given
        double invalidLongitude = 180.000001;

        // when & then
        assertThatThrownBy(
            () ->
                accommodationElasticsearchLocationSearchService
                    .searchNearby(
                        null,
                        37.5665,
                        invalidLongitude,
                        5.0,
                        20
                    )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.ACCOMMODATION_LONGITUDE_OUT_OF_RANGE
            );

        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should(never())
            .searchNearby(
                null,
                37.5665,
                invalidLongitude,
                5.0,
                20
            );
    }

    @Test
    void 유효하지_않은_검색_반경이면_예외가_발생한다() {
        // given
        double invalidRadiusKm = 0.0;

        // when & then
        assertThatThrownBy(
            () ->
                accommodationElasticsearchLocationSearchService
                    .searchNearby(
                        null,
                        37.5665,
                        126.9780,
                        invalidRadiusKm,
                        20
                    )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should(never())
            .searchNearby(
                null,
                37.5665,
                126.9780,
                invalidRadiusKm,
                20
            );
    }

    @Test
    void limit이_허용_범위를_벗어나면_예외가_발생한다() {
        // given
        int invalidLimit = 101;

        // when & then
        assertThatThrownBy(
            () ->
                accommodationElasticsearchLocationSearchService
                    .searchNearby(
                        null,
                        37.5665,
                        126.9780,
                        5.0,
                        invalidLimit
                    )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should(never())
            .searchNearby(
                null,
                37.5665,
                126.9780,
                5.0,
                invalidLimit
            );
    }

    @Test
    void 위도와_경도의_경계값은_허용한다() {
        // given
        given(
            accommodationElasticsearchLocationSearchRepository.searchNearby(
                null,
                90.0,
                180.0,
                5.0,
                20
            )
        ).willReturn(
            List.of()
        );

        // when
        accommodationElasticsearchLocationSearchService.searchNearby(
            null,
            90.0,
            180.0,
            5.0,
            20
        );

        // then
        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should()
            .searchNearby(
                null,
                90.0,
                180.0,
                5.0,
                20
            );
    }

    @Test
    void 검색_반경과_limit의_최대값은_허용한다() {
        // given
        given(
            accommodationElasticsearchLocationSearchRepository.searchNearby(
                null,
                37.5665,
                126.9780,
                100.0,
                100
            )
        ).willReturn(
            List.of()
        );

        // when
        accommodationElasticsearchLocationSearchService.searchNearby(
            null,
            37.5665,
            126.9780,
            100.0,
            100
        );

        // then
        then(
            accommodationElasticsearchLocationSearchRepository
        )
            .should()
            .searchNearby(
                null,
                37.5665,
                126.9780,
                100.0,
                100
            );
    }
}
