package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.repository.AccommodationLocationSearchProjection;
import com.roompick.domain.accommodation.repository.AccommodationLocationSearchRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * AccommodationLocationSearchService의
 * 위치 검색 조건 검증과 Bounding Box 계산,
 * DTO 변환을 검증하는 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationLocationSearchServiceTest {

    private static final double LATITUDE = 37.566500;
    private static final double LONGITUDE = 126.978000;
    private static final double RADIUS_KM = 5.0;
    private static final int LIMIT = 20;

    @Mock
    private AccommodationLocationSearchRepository
        accommodationLocationSearchRepository;

    @Mock
    private AccommodationLocationSearchProjection projection;

    @InjectMocks
    private AccommodationLocationSearchService
        accommodationLocationSearchService;

    @Test
    void 위치_기반_숙소_검색에_성공한다() {
        // given
        String keyword = "룸픽";

        given(projection.getAccommodationId())
            .willReturn(1L);
        given(projection.getName())
            .willReturn("룸픽 서울 호텔");
        given(projection.getAddress())
            .willReturn("서울특별시 중구");
        given(projection.getLatitude())
            .willReturn(37.565800);
        given(projection.getLongitude())
            .willReturn(126.978500);
        given(projection.getDistanceMeters())
            .willReturn(850.0);

        givenRepositoryResult(
            keyword,
            LATITUDE,
            LONGITUDE,
            RADIUS_KM,
            LIMIT,
            List.of(projection)
        );

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );

        // then
        assertThat(result)
            .hasSize(1);

        AccommodationLocationSearchResponseDto response =
            result.get(0);

        assertThat(response.accommodationId())
            .isEqualTo(1L);
        assertThat(response.name())
            .isEqualTo("룸픽 서울 호텔");
        assertThat(response.address())
            .isEqualTo("서울특별시 중구");
        assertThat(response.latitude())
            .isEqualTo(37.565800);
        assertThat(response.longitude())
            .isEqualTo(126.978500);
        assertThat(response.distanceKm())
            .isEqualTo(0.85);

        /*
         * Service가 검색 중심과 반경으로 Bounding Box를 계산한 뒤
         * Repository에 전달하는지도 함께 검증합니다.
         */
        thenRepositorySearched(
            keyword,
            LATITUDE,
            LONGITUDE,
            RADIUS_KM,
            LIMIT
        );
    }

    @Test
    void 검색어의_앞뒤_공백을_제거한_뒤_검색한다() {
        // given
        String keyword = "  룸픽  ";
        String normalizedKeyword = "룸픽";

        givenRepositoryResult(
            normalizedKeyword,
            LATITUDE,
            LONGITUDE,
            RADIUS_KM,
            LIMIT,
            List.of()
        );

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );

        // then
        assertThat(result)
            .isEmpty();

        thenRepositorySearched(
            normalizedKeyword,
            LATITUDE,
            LONGITUDE,
            RADIUS_KM,
            LIMIT
        );
    }

    @Test
    void 검색어가_공백이면_keyword_조건을_사용하지_않는다() {
        // given
        String keyword = "   ";

        givenRepositoryResult(
            null,
            LATITUDE,
            LONGITUDE,
            RADIUS_KM,
            LIMIT,
            List.of()
        );

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );

        // then
        assertThat(result)
            .isEmpty();

        thenRepositorySearched(
            null,
            LATITUDE,
            LONGITUDE,
            RADIUS_KM,
            LIMIT
        );
    }

    @ParameterizedTest
    @ValueSource(
        doubles = {
            -90.000001,
            90.000001,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        }
    )
    void 위도가_허용_범위를_벗어나면_예외가_발생한다(
        double latitude
    ) {
        assertThatThrownBy(
            () -> accommodationLocationSearchService.searchNearby(
                null,
                latitude,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.ACCOMMODATION_LATITUDE_OUT_OF_RANGE
            );

        then(accommodationLocationSearchRepository)
            .shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(
        doubles = {
            -180.000001,
            180.000001,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        }
    )
    void 경도가_허용_범위를_벗어나면_예외가_발생한다(
        double longitude
    ) {
        assertThatThrownBy(
            () -> accommodationLocationSearchService.searchNearby(
                null,
                LATITUDE,
                longitude,
                RADIUS_KM,
                LIMIT
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.ACCOMMODATION_LONGITUDE_OUT_OF_RANGE
            );

        then(accommodationLocationSearchRepository)
            .shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(
        doubles = {
            -1.0,
            0.0,
            100.000001,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        }
    )
    void 검색_반경이_허용_범위를_벗어나면_예외가_발생한다(
        double radiusKm
    ) {
        assertThatThrownBy(
            () -> accommodationLocationSearchService.searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                radiusKm,
                LIMIT
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        then(accommodationLocationSearchRepository)
            .shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void 검색_limit이_허용_범위를_벗어나면_예외가_발생한다(
        int limit
    ) {
        assertThatThrownBy(
            () -> accommodationLocationSearchService.searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                limit
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        then(accommodationLocationSearchRepository)
            .shouldHaveNoInteractions();
    }

    @Test
    void 위도와_경도의_경계값은_검색할_수_있다() {
        // given
        double latitude = 90.0;
        double longitude = 180.0;

        givenRepositoryResult(
            null,
            latitude,
            longitude,
            RADIUS_KM,
            LIMIT,
            List.of()
        );

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                null,
                latitude,
                longitude,
                RADIUS_KM,
                LIMIT
            );

        // then
        assertThat(result)
            .isEmpty();

        thenRepositorySearched(
            null,
            latitude,
            longitude,
            RADIUS_KM,
            LIMIT
        );
    }

    @Test
    void 검색_반경과_limit의_최대값은_허용한다() {
        // given
        double radiusKm = 100.0;
        int limit = 100;

        givenRepositoryResult(
            null,
            LATITUDE,
            LONGITUDE,
            radiusKm,
            limit,
            List.of()
        );

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                radiusKm,
                limit
            );

        // then
        assertThat(result)
            .isEmpty();

        thenRepositorySearched(
            null,
            LATITUDE,
            LONGITUDE,
            radiusKm,
            limit
        );
    }

    /**
     * Service가 계산할 것과 동일한 Bounding Box를 이용해
     * Repository Mock 반환값을 설정합니다.
     */
    private void givenRepositoryResult(
        String keyword,
        double latitude,
        double longitude,
        double radiusKm,
        int limit,
        List<AccommodationLocationSearchProjection> results
    ) {
        AccommodationLocationBoundingBox boundingBox =
            AccommodationLocationBoundingBox.calculate(
                latitude,
                longitude,
                radiusKm
            );

        given(
            accommodationLocationSearchRepository.searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                boundingBox.minLatitude(),
                boundingBox.maxLatitude(),
                boundingBox.minLongitude(),
                boundingBox.maxLongitude(),
                limit
            )
        ).willReturn(results);
    }

    /**
     * Service가 계산한 Bounding Box가 Repository까지
     * 정확하게 전달됐는지 검증합니다.
     */
    private void thenRepositorySearched(
        String keyword,
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        AccommodationLocationBoundingBox boundingBox =
            AccommodationLocationBoundingBox.calculate(
                latitude,
                longitude,
                radiusKm
            );

        then(accommodationLocationSearchRepository)
            .should()
            .searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                boundingBox.minLatitude(),
                boundingBox.maxLatitude(),
                boundingBox.minLongitude(),
                boundingBox.maxLongitude(),
                limit
            );
    }
}
