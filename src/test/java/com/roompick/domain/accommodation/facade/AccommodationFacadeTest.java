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
import org.springframework.dao.DataAccessResourceFailureException;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.exception.PopularAccommodationRankingUnavailableException;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationQueryService;
import com.roompick.domain.accommodation.service.PopularAccommodationRankingService;
import com.roompick.domain.accommodation.service.PopularAccommodationSingleFlightService;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.domain.room.service.RoomService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 사용자 숙소 조회 흐름과 인기 숙소 장애 대응 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationFacadeTest {

    @Mock
    private AccommodationService accommodationService;

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
    @DisplayName("운영 중인 숙소 상세 조회에 성공하면 인기 점수를 기록한다")
    void 운영_중인_숙소_상세_조회에_성공하면_인기_점수를_기록한다() {
        Long accommodationId = 1L;
        LocalTime checkInTime = LocalTime.of(15, 0);
        LocalTime checkOutTime = LocalTime.of(11, 0);
        Accommodation accommodation = Accommodation.create(
            "룸픽 호텔",
            "서울특별시 중구",
            "인기 숙소 랭킹 테스트용 숙소",
            checkInTime,
            checkOutTime
        );
        given(accommodationService.findActiveById(accommodationId))
            .willReturn(accommodation);

        AccommodationDetailResponseDto response =
            accommodationFacade.getAccommodationDetail(accommodationId);

        assertThat(response.name()).isEqualTo("룸픽 호텔");
        assertThat(response.address()).isEqualTo("서울특별시 중구");
        assertThat(response.description())
            .isEqualTo("인기 숙소 랭킹 테스트용 숙소");
        assertThat(response.checkInTime()).isEqualTo(checkInTime);
        assertThat(response.checkOutTime()).isEqualTo(checkOutTime);
        then(accommodationService).should().findActiveById(accommodationId);
        then(popularAccommodationRankingService)
            .should()
            .recordView(accommodationId);
    }

    @Test
    @DisplayName("숙소 상세 조회에 실패하면 인기 점수를 기록하지 않는다")
    void 숙소_상세_조회에_실패하면_인기_점수를_기록하지_않는다() {
        Long accommodationId = 999L;
        RuntimeException exception = new RuntimeException("숙소 조회 실패");
        given(accommodationService.findActiveById(accommodationId))
            .willThrow(exception);

        assertThatThrownBy(
            () -> accommodationFacade.getAccommodationDetail(accommodationId)
        ).isSameAs(exception);
        then(popularAccommodationRankingService).shouldHaveNoInteractions();
    }

    @Test
    void 정상_인기_숙소_결과를_Single_Flight_최종_작업으로_반환한다() {
        shareFinalOperation();
        int limit = 2;
        List<PopularAccommodationResponseDto> expected = result(3L, 1L);
        given(popularAccommodationQueryService.getPopularAccommodations(
            PopularAccommodationPeriod.DAILY,
            limit
        )).willReturn(expected);

        List<PopularAccommodationResponseDto> actual =
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        assertThat(actual).isSameAs(expected);
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
        then(accommodationService).shouldHaveNoInteractions();
    }

    @Test
    void DAILY와_WEEKLY_Redis_랭킹_장애는_공통_최종_작업에서_fallback한다() {
        shareFinalOperation();
        for (PopularAccommodationPeriod period
            : PopularAccommodationPeriod.values()) {
            int limit = 2;
            PopularAccommodationRankingUnavailableException exception =
                new PopularAccommodationRankingUnavailableException(
                    new DataAccessResourceFailureException("Redis 연결 실패")
                );
            List<AccommodationListResponseDto> latest = List.of(
                new AccommodationListResponseDto(10L, "최근 숙소", "서울"),
                new AccommodationListResponseDto(8L, "다음 숙소", "부산")
            );
            given(popularAccommodationQueryService.getPopularAccommodations(
                period,
                limit
            )).willThrow(exception);
            given(accommodationService.findLatestActive(limit))
                .willReturn(latest);

            List<PopularAccommodationResponseDto> actual =
                accommodationFacade.getPopularAccommodations(period, limit);

            assertThat(actual)
                .extracting(PopularAccommodationResponseDto::rank)
                .containsExactly(1, 2);
            assertThat(actual)
                .extracting(PopularAccommodationResponseDto::accommodationId)
                .containsExactly(10L, 8L);
        }

        then(accommodationService)
            .should(org.mockito.Mockito.times(2))
            .findLatestActive(2);
    }

    @Test
    void DB_조회_장애는_fallback하지_않고_그대로_전파한다() {
        shareFinalOperation();
        int limit = 2;
        DataAccessResourceFailureException databaseException =
            new DataAccessResourceFailureException("DB 연결 실패");
        given(popularAccommodationQueryService.getPopularAccommodations(
            PopularAccommodationPeriod.DAILY,
            limit
        )).willThrow(databaseException);

        assertThatThrownBy(
            () -> accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            )
        ).isSameAs(databaseException);
        then(accommodationService).shouldHaveNoInteractions();
    }

    @Test
    void 비즈니스_예외는_fallback하지_않고_그대로_전파한다() {
        shareFinalOperation();
        int invalidLimit = 0;
        BusinessException exception =
            new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        given(popularAccommodationQueryService.getPopularAccommodations(
            PopularAccommodationPeriod.DAILY,
            invalidLimit
        )).willThrow(exception);

        assertThatThrownBy(
            () -> accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                invalidLimit
            )
        ).isSameAs(exception);
        then(accommodationService).shouldHaveNoInteractions();
    }

    private List<PopularAccommodationResponseDto> result(Long... ids) {
        java.util.ArrayList<PopularAccommodationResponseDto> responses =
            new java.util.ArrayList<>();
        for (Long id : ids) {
            responses.add(PopularAccommodationResponseDto.from(
                responses.size() + 1,
                new AccommodationListResponseDto(
                    id,
                    "숙소 " + id,
                    "서울"
                )
            ));
        }
        return responses;
    }
}
