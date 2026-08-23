package com.roompick.domain.accommodation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.exception.PopularAccommodationRankingUnavailableException;
import com.roompick.domain.accommodation.service.AccommodationElasticsearchLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationQueryService;
import com.roompick.domain.accommodation.service.PopularAccommodationRankingService;
import com.roompick.domain.accommodation.service.PopularAccommodationSingleFlightService;
import com.roompick.domain.accommodation.type.AccommodationLocationSearchEngine;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.domain.room.service.RoomService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 사용자 숙소 조회 흐름과
 * 위치 검색 엔진 선택 및 인기 숙소 장애 대응 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationFacadeTest {

    @Mock
    private AccommodationService accommodationService;

    @Mock
    private AccommodationLocationSearchService
        accommodationLocationSearchService;

    @Mock
    private ObjectProvider<AccommodationElasticsearchLocationSearchService>
        accommodationElasticsearchLocationSearchServiceProvider;

    @Mock
    private AccommodationElasticsearchLocationSearchService
        accommodationElasticsearchLocationSearchService;

    @Mock
    private RoomService roomService;

    @Mock
    private PopularAccommodationRankingService
        popularAccommodationRankingService;

    @Mock
    private PopularAccommodationSingleFlightService
        popularAccommodationSingleFlightService;

    @Mock
    private PopularAccommodationQueryService
        popularAccommodationQueryService;

    @InjectMocks
    private AccommodationFacade accommodationFacade;

    /**
     * Single Flight 내부의 최종 작업을
     * 테스트에서 실제로 실행하도록 Mock 동작을 설정합니다.
     */
    private void shareFinalOperation() {
        given(
            popularAccommodationSingleFlightService.execute(
                any(PopularAccommodationPeriod.class),
                anyInt(),
                any()
            )
        ).willAnswer(invocation -> {
            Supplier<List<PopularAccommodationResponseDto>> operation =
                invocation.getArgument(2);

            return operation.get();
        });
    }

    @Test
    @DisplayName(
        "위치 검색 엔진이 MYSQL이면 MySQL Bounding Box 검색을 사용한다"
    )
    void 위치_검색_엔진이_MYSQL이면_MySQL_Bounding_Box_검색을_사용한다() {
        // given
        String keyword = "룸픽";
        double latitude = 37.5665;
        double longitude = 126.9780;
        double radiusKm = 5.0;
        int limit = 20;

        List<AccommodationLocationSearchResponseDto> expected =
            List.of(
                new AccommodationLocationSearchResponseDto(
                    1L,
                    "룸픽 서울 호텔",
                    "서울특별시 중구",
                    37.5658,
                    126.9785,
                    0.85
                )
            );

        /*
         * Mockito 단위 테스트에서는 @Value가 자동으로 주입되지 않으므로
         * 테스트할 검색 엔진을 명시적으로 설정합니다.
         */
        ReflectionTestUtils.setField(
            accommodationFacade,
            "locationSearchEngine",
            AccommodationLocationSearchEngine.MYSQL
        );

        given(
            accommodationLocationSearchService.searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            )
        ).willReturn(expected);

        // when
        List<AccommodationLocationSearchResponseDto> actual =
            accommodationFacade.searchNearbyAccommodations(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        // then
        assertThat(actual)
            .isSameAs(expected);

        then(accommodationLocationSearchService)
            .should()
            .searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        /*
         * MYSQL이 선택된 경우
         * Elasticsearch 검색 경로는 호출되면 안 됩니다.
         */
        then(accommodationElasticsearchLocationSearchServiceProvider)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "위치 검색 엔진이 ELASTICSEARCH이면 Elasticsearch 검색을 사용한다"
    )
    void 위치_검색_엔진이_ELASTICSEARCH이면_Elasticsearch_검색을_사용한다() {
        // given
        String keyword = "룸픽";
        double latitude = 37.5665;
        double longitude = 126.9780;
        double radiusKm = 5.0;
        int limit = 20;

        List<AccommodationLocationSearchResponseDto> expected =
            List.of(
                new AccommodationLocationSearchResponseDto(
                    1L,
                    "룸픽 서울 호텔",
                    "서울특별시 중구",
                    37.5658,
                    126.9785,
                    0.85
                )
            );

        ReflectionTestUtils.setField(
            accommodationFacade,
            "locationSearchEngine",
            AccommodationLocationSearchEngine.ELASTICSEARCH
        );

        given(
            accommodationElasticsearchLocationSearchServiceProvider
                .getIfAvailable()
        ).willReturn(
            accommodationElasticsearchLocationSearchService
        );

        accommodationFacade.initializeLocationSearchEngine();

        given(
            accommodationElasticsearchLocationSearchService.searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            )
        ).willReturn(expected);

        // when
        List<AccommodationLocationSearchResponseDto> actual =
            accommodationFacade.searchNearbyAccommodations(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        // then
        assertThat(actual)
            .isSameAs(expected);

        then(accommodationElasticsearchLocationSearchService)
            .should()
            .searchNearby(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        /*
         * ELASTICSEARCH가 선택된 경우
         * MySQL Bounding Box 검색은 실행되면 안 됩니다.
         */
        then(accommodationLocationSearchService)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "ELASTICSEARCH 설정에 검색 Bean이 없으면 명확한 설정 예외가 발생한다"
    )
    void Elasticsearch_설정과_Bean_활성화가_불일치하면_예외가_발생한다() {
        // given
        ReflectionTestUtils.setField(
            accommodationFacade,
            "locationSearchEngine",
            AccommodationLocationSearchEngine.ELASTICSEARCH
        );

        given(
            accommodationElasticsearchLocationSearchServiceProvider
                .getIfAvailable()
        ).willReturn(null);

        // when & then
        assertThatThrownBy(
            () -> accommodationFacade.initializeLocationSearchEngine()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                "위치 검색 엔진은 ELASTICSEARCH"
            );

        then(accommodationLocationSearchService)
            .shouldHaveNoInteractions();

        then(accommodationElasticsearchLocationSearchService)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("운영 중인 숙소 상세 조회에 성공하면 인기 점수를 기록한다")
    void 운영_중인_숙소_상세_조회에_성공하면_인기_점수를_기록한다() {
        Long accommodationId = 1L;
        LocalTime checkInTime = LocalTime.of(15, 0);
        LocalTime checkOutTime = LocalTime.of(11, 0);

        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 중구",
                "인기 숙소 랭킹 테스트용 숙소",
                checkInTime,
                checkOutTime
            );

        given(
            accommodationService.findActiveByIdWithImages(
                accommodationId
            )
        ).willReturn(accommodation);

        AccommodationDetailResponseDto response =
            accommodationFacade.getAccommodationDetail(
                accommodationId,
                false
            );

        assertThat(response.name())
            .isEqualTo("룸픽 호텔");

        assertThat(response.address())
            .isEqualTo("서울특별시 중구");

        assertThat(response.description())
            .isEqualTo("인기 숙소 랭킹 테스트용 숙소");

        assertThat(response.checkInTime())
            .isEqualTo(checkInTime);

        assertThat(response.checkOutTime())
            .isEqualTo(checkOutTime);

        then(accommodationService)
            .should()
            .findActiveByIdWithImages(
                accommodationId
            );

        then(popularAccommodationRankingService)
            .should()
            .recordView(
                accommodationId
            );
    }

    @Test
    @DisplayName("숙소 상세 조회에 실패하면 인기 점수를 기록하지 않는다")
    void 숙소_상세_조회에_실패하면_인기_점수를_기록하지_않는다() {
        Long accommodationId = 999L;

        RuntimeException exception =
            new RuntimeException(
                "숙소 조회 실패"
            );

        given(
            accommodationService.findActiveByIdWithImages(
                accommodationId
            )
        ).willThrow(exception);

        assertThatThrownBy(
            () -> accommodationFacade.getAccommodationDetail(
                accommodationId,
                false
            )
        ).isSameAs(exception);

        then(popularAccommodationRankingService)
            .shouldHaveNoInteractions();
    }

    @Test
    void 정상_인기_숙소_결과를_Single_Flight_최종_작업으로_반환한다() {
        shareFinalOperation();

        int limit = 2;

        List<PopularAccommodationResponseDto> expected =
            result(
                3L,
                1L
            );

        given(
            popularAccommodationQueryService.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            )
        ).willReturn(expected);

        List<PopularAccommodationResponseDto> actual =
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        assertThat(actual)
            .isSameAs(expected);

        then(popularAccommodationSingleFlightService)
            .should()
            .execute(
                org.mockito.ArgumentMatchers.eq(
                    PopularAccommodationPeriod.DAILY
                ),
                org.mockito.ArgumentMatchers.eq(limit),
                any()
            );

        then(popularAccommodationQueryService)
            .should()
            .getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        then(accommodationService)
            .shouldHaveNoInteractions();
    }

    @Test
    void DAILY와_WEEKLY_Redis_랭킹_장애는_공통_최종_작업에서_fallback한다() {
        shareFinalOperation();

        for (
            PopularAccommodationPeriod period
            : PopularAccommodationPeriod.values()
        ) {
            int limit = 2;

            PopularAccommodationRankingUnavailableException exception =
                new PopularAccommodationRankingUnavailableException(
                    new DataAccessResourceFailureException(
                        "Redis 연결 실패"
                    )
                );

            List<AccommodationListResponseDto> latest =
                List.of(
                    new AccommodationListResponseDto(
                        10L,
                        "최근 숙소",
                        "서울"
                    ),
                    new AccommodationListResponseDto(
                        8L,
                        "다음 숙소",
                        "부산"
                    )
                );

            given(
                popularAccommodationQueryService.getPopularAccommodations(
                    period,
                    limit
                )
            ).willThrow(exception);

            given(
                accommodationService.findLatestActive(
                    limit
                )
            ).willReturn(latest);

            List<PopularAccommodationResponseDto> actual =
                accommodationFacade.getPopularAccommodations(
                    period,
                    limit
                );

            assertThat(actual)
                .extracting(
                    PopularAccommodationResponseDto::rank
                )
                .containsExactly(
                    1,
                    2
                );

            assertThat(actual)
                .extracting(
                    PopularAccommodationResponseDto::accommodationId
                )
                .containsExactly(
                    10L,
                    8L
                );
        }

        then(accommodationService)
            .should(
                org.mockito.Mockito.times(2)
            )
            .findLatestActive(
                2
            );
    }

    @Test
    void DB_조회_장애는_fallback하지_않고_그대로_전파한다() {
        shareFinalOperation();

        int limit = 2;

        DataAccessResourceFailureException databaseException =
            new DataAccessResourceFailureException(
                "DB 연결 실패"
            );

        given(
            popularAccommodationQueryService.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            )
        ).willThrow(databaseException);

        assertThatThrownBy(
            () -> accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            )
        ).isSameAs(databaseException);

        then(accommodationService)
            .shouldHaveNoInteractions();
    }

    @Test
    void 비즈니스_예외는_fallback하지_않고_그대로_전파한다() {
        shareFinalOperation();

        int invalidLimit = 0;

        BusinessException exception =
            new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );

        given(
            popularAccommodationQueryService.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                invalidLimit
            )
        ).willThrow(exception);

        assertThatThrownBy(
            () -> accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                invalidLimit
            )
        ).isSameAs(exception);

        then(accommodationService)
            .shouldHaveNoInteractions();
    }

    /**
     * 인기 숙소 테스트용 응답 목록을 생성합니다.
     */
    private List<PopularAccommodationResponseDto> result(
        Long... ids
    ) {
        java.util.ArrayList<PopularAccommodationResponseDto> responses =
            new java.util.ArrayList<>();

        for (Long id : ids) {
            responses.add(
                PopularAccommodationResponseDto.from(
                    responses.size() + 1,
                    new AccommodationListResponseDto(
                        id,
                        "숙소 " + id,
                        "서울"
                    )
                )
            );
        }

        return responses;
    }
}
