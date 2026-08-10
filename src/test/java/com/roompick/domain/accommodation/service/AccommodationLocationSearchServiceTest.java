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
 * 위치 검색 조건 검증과 DTO 변환을 검증하는 단위 테스트입니다.
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
        // given: MySQL 위치 검색 결과가 존재합니다.
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

        given(
            accommodationLocationSearchRepository.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            )
        ).willReturn(
            List.of(projection)
        );

        // when: 위치와 keyword 조건으로 숙소를 검색합니다.
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );

        // then: Projection 결과가 응답 DTO로 변환됩니다.
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

        /*
         * Repository가 반환한 거리는 미터 단위이고,
         * API 응답에서는 킬로미터 단위로 변환됩니다.
         */
        assertThat(response.distanceKm())
            .isEqualTo(0.85);
    }

    @Test
    void 검색어의_앞뒤_공백을_제거한_뒤_검색한다() {
        // given: 앞뒤 공백이 포함된 검색어를 전달합니다.
        String keyword = "  룸픽  ";
        String normalizedKeyword = "룸픽";

        given(
            accommodationLocationSearchRepository.searchNearby(
                normalizedKeyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            )
        ).willReturn(
            List.of()
        );

        // when: 위치 검색을 수행합니다.
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );

        // then: 검색어의 앞뒤 공백을 제거하여 Repository에 전달합니다.
        assertThat(result)
            .isEmpty();

        then(accommodationLocationSearchRepository)
            .should()
            .searchNearby(
                normalizedKeyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );
    }

    @Test
    void 검색어가_공백이면_keyword_조건을_사용하지_않는다() {
        // given: 검색어가 공백 문자열입니다.
        String keyword = "   ";

        given(
            accommodationLocationSearchRepository.searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            )
        ).willReturn(
            List.of()
        );

        // when: 위치 검색을 수행합니다.
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                keyword,
                LATITUDE,
                LONGITUDE,
                RADIUS_KM,
                LIMIT
            );

        // then: Repository에는 keyword가 null로 전달됩니다.
        assertThat(result)
            .isEmpty();

        then(accommodationLocationSearchRepository)
            .should()
            .searchNearby(
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
        // when & then: 잘못된 위도는 Repository 조회 전에 차단됩니다.
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
        // when & then: 잘못된 경도는 Repository 조회 전에 차단됩니다.
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
        // when & then: 잘못된 반경은 DB 쿼리 실행 전에 차단됩니다.
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
        // when & then: 잘못된 limit은 DB 조회 전에 차단됩니다.
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
        // given: 위도와 경도의 최대 경계값을 사용합니다.
        double latitude = 90.0;
        double longitude = 180.0;

        given(
            accommodationLocationSearchRepository.searchNearby(
                null,
                latitude,
                longitude,
                RADIUS_KM,
                LIMIT
            )
        ).willReturn(
            List.of()
        );

        // when: 허용된 좌표 경계값으로 검색합니다.
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                null,
                latitude,
                longitude,
                RADIUS_KM,
                LIMIT
            );

        // then: 입력 검증을 통과하고 Repository가 호출됩니다.
        assertThat(result)
            .isEmpty();

        then(accommodationLocationSearchRepository)
            .should()
            .searchNearby(
                null,
                latitude,
                longitude,
                RADIUS_KM,
                LIMIT
            );
    }

    @Test
    void 검색_반경과_limit의_최대값은_허용한다() {
        // given: 현재 정책의 최대 검색 조건을 사용합니다.
        double radiusKm = 100.0;
        int limit = 100;

        given(
            accommodationLocationSearchRepository.searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                radiusKm,
                limit
            )
        ).willReturn(
            List.of()
        );

        // when: 최대 허용값으로 검색합니다.
        List<AccommodationLocationSearchResponseDto> result =
            accommodationLocationSearchService.searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                radiusKm,
                limit
            );

        // then: 검증을 통과하고 Repository가 호출됩니다.
        assertThat(result)
            .isEmpty();

        then(accommodationLocationSearchRepository)
            .should()
            .searchNearby(
                null,
                LATITUDE,
                LONGITUDE,
                radiusKm,
                limit
            );
    }
}
