package com.roompick.domain.accommodation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalTime;
import java.util.List;

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
import com.roompick.domain.accommodation.service.PopularAccommodationSingleFlightService;
import com.roompick.domain.accommodation.service.PopularAccommodationRankingService;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.domain.room.service.RoomService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 사용자 숙소 조회 흐름과
 * 인기 숙소 장애 대응 흐름을 검증합니다.
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

    @InjectMocks
    private AccommodationFacade accommodationFacade;

    @Test
    @DisplayName(
        "운영 중인 숙소 상세 조회에 성공하면 "
            + "인기 점수를 기록한다"
    )
    void 운영_중인_숙소_상세_조회에_성공하면_인기_점수를_기록한다() {
        // given: 운영 중인 숙소를 준비합니다.
        Long accommodationId = 1L;

        LocalTime checkInTime =
            LocalTime.of(
                15,
                0
            );

        LocalTime checkOutTime =
            LocalTime.of(
                11,
                0
            );

        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 중구",
                "인기 숙소 랭킹 테스트용 숙소",
                checkInTime,
                checkOutTime
            );

        given(
            accommodationService.findActiveById(
                accommodationId
            )
        ).willReturn(
            accommodation
        );

        // when: 숙소 상세 정보를 조회합니다.
        AccommodationDetailResponseDto response =
            accommodationFacade.getAccommodationDetail(
                accommodationId
            );

        // then: 숙소 공개 정보가 응답에 포함됩니다.
        assertThat(response.name())
            .isEqualTo("룸픽 호텔");

        assertThat(response.address())
            .isEqualTo("서울특별시 중구");

        assertThat(response.description())
            .isEqualTo(
                "인기 숙소 랭킹 테스트용 숙소"
            );

        assertThat(response.checkInTime())
            .isEqualTo(checkInTime);

        assertThat(response.checkOutTime())
            .isEqualTo(checkOutTime);

        /*
         * 정상적으로 상세 응답이 생성된 경우에만
         * Redis 인기 점수 기록을 요청합니다.
         */
        then(accommodationService)
            .should()
            .findActiveById(
                accommodationId
            );

        then(popularAccommodationRankingService)
            .should()
            .recordView(
                accommodationId
            );
    }

    @Test
    @DisplayName(
        "숙소 상세 조회에 실패하면 "
            + "인기 점수를 기록하지 않는다"
    )
    void 숙소_상세_조회에_실패하면_인기_점수를_기록하지_않는다() {
        // given: 숙소 DB 조회에서 예외가 발생합니다.
        Long accommodationId = 999L;

        RuntimeException exception =
            new RuntimeException(
                "숙소 조회 실패"
            );

        given(
            accommodationService.findActiveById(
                accommodationId
            )
        ).willThrow(
            exception
        );

        // when & then: 조회 예외가 그대로 전달됩니다.
        assertThatThrownBy(
            () ->
                accommodationFacade
                    .getAccommodationDetail(
                        accommodationId
                    )
        ).isSameAs(
            exception
        );

        /*
         * 숙소 조회와 DTO 생성이 완료되지 않았으므로
         * Redis 인기 점수는 기록하지 않습니다.
         */
        then(popularAccommodationRankingService)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "Redis 인기 숙소 조회가 성공하면 "
            + "조회 Service의 결과를 그대로 반환한다"
    )
    void Redis_인기_숙소_조회가_성공하면_결과를_그대로_반환한다() {
        // given: 캐시 또는 Redis 랭킹 기반 조회 결과를 준비합니다.
        int limit = 2;

        List<PopularAccommodationResponseDto> expected =
            List.of(
                PopularAccommodationResponseDto.from(
                    1,
                    new AccommodationListResponseDto(
                        3L,
                        "룸픽 부산 호텔",
                        "부산광역시 해운대구"
                    )
                ),
                PopularAccommodationResponseDto.from(
                    2,
                    new AccommodationListResponseDto(
                        1L,
                        "룸픽 서울 호텔",
                        "서울특별시 중구"
                    )
                )
            );

        given(
            popularAccommodationSingleFlightService
                .getPopularAccommodations(
                    PopularAccommodationPeriod.DAILY,
                    limit
                )
        ).willReturn(
            expected
        );

        // when: 인기 숙소 목록을 조회합니다.
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        // then: QueryService의 결과를 그대로 반환합니다.
        assertThat(result)
            .isSameAs(expected);

        then(popularAccommodationSingleFlightService)
            .should()
            .getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        /*
         * Redis 조회가 정상 처리됐으므로
         * DB fallback 조회는 실행하지 않습니다.
         */
        then(accommodationService)
            .shouldHaveNoInteractions();

        then(popularAccommodationRankingService)
            .shouldHaveNoInteractions();
    }

    @Test
    void WEEKLY_Redis_랭킹_장애도_최신_ACTIVE_숙소로_fallback한다() {
        int limit = 1;
        PopularAccommodationRankingUnavailableException exception =
            new PopularAccommodationRankingUnavailableException(
                new DataAccessResourceFailureException("Redis 연결 실패")
            );
        AccommodationListResponseDto accommodation =
            new AccommodationListResponseDto(
                10L,
                "최근 숙소",
                "서울특별시"
            );

        given(
            popularAccommodationSingleFlightService.getPopularAccommodations(
                PopularAccommodationPeriod.WEEKLY,
                limit
            )
        ).willThrow(exception);
        given(accommodationService.findLatestActive(limit))
            .willReturn(List.of(accommodation));

        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.WEEKLY,
                limit
            );

        assertThat(result).extracting(
            PopularAccommodationResponseDto::rank
        ).containsExactly(1);
        then(accommodationService).should().findLatestActive(limit);
    }

    @Test
    void WEEKLY_DB_조회_장애는_fallback하지_않는다() {
        int limit = 1;
        DataAccessResourceFailureException databaseException =
            new DataAccessResourceFailureException("DB 연결 실패");
        given(
            popularAccommodationSingleFlightService.getPopularAccommodations(
                PopularAccommodationPeriod.WEEKLY,
                limit
            )
        ).willThrow(databaseException);

        assertThatThrownBy(
            () -> accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.WEEKLY,
                limit
            )
        ).isSameAs(databaseException);
        then(accommodationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "Redis 인기 숙소 조회에 실패하면 "
            + "최신 ACTIVE 숙소를 임시 fallback으로 반환한다"
    )
    void Redis_인기_숙소_조회에_실패하면_DB_fallback을_반환한다() {
        // given: Redis 조회 과정에서 연결 장애가 발생합니다.
        int limit = 2;

        DataAccessResourceFailureException redisException =
            new DataAccessResourceFailureException(
                "Redis 연결 실패"
            );

        PopularAccommodationRankingUnavailableException
            rankingUnavailableException =
            new PopularAccommodationRankingUnavailableException(
                redisException
            );

        given(
            popularAccommodationSingleFlightService
                .getPopularAccommodations(
                    PopularAccommodationPeriod.DAILY,
                    limit
                )
        ).willThrow(
            rankingUnavailableException
        );

        /*
         * fallback은 실제 인기 순위가 아니라
         * 최신 등록 순서로 조회한 ACTIVE 숙소 목록입니다.
         */
        List<AccommodationListResponseDto>
            latestActiveAccommodations =
            List.of(
                new AccommodationListResponseDto(
                    10L,
                    "최근 등록된 룸픽 호텔",
                    "서울특별시 강남구"
                ),
                new AccommodationListResponseDto(
                    8L,
                    "두 번째 최신 룸픽 호텔",
                    "서울특별시 종로구"
                )
            );

        given(
            accommodationService.findLatestActive(
                limit
            )
        ).willReturn(
            latestActiveAccommodations
        );

        // when: 인기 숙소 목록을 조회합니다.
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        // then: API 응답 형식을 유지한 임시 목록을 반환합니다.
        assertThat(result)
            .hasSize(2);

        /*
         * fallback의 rank는 실제 인기 순위가 아니라
         * 응답 형식을 유지하기 위한 임시 순번입니다.
         */
        assertThat(result.get(0).rank())
            .isEqualTo(1);

        assertThat(result.get(0).accommodationId())
            .isEqualTo(10L);

        assertThat(result.get(0).name())
            .isEqualTo(
                "최근 등록된 룸픽 호텔"
            );

        assertThat(result.get(0).address())
            .isEqualTo(
                "서울특별시 강남구"
            );

        assertThat(result.get(1).rank())
            .isEqualTo(2);

        assertThat(result.get(1).accommodationId())
            .isEqualTo(8L);

        assertThat(result.get(1).name())
            .isEqualTo(
                "두 번째 최신 룸픽 호텔"
            );

        assertThat(result.get(1).address())
            .isEqualTo(
                "서울특별시 종로구"
            );

        then(popularAccommodationSingleFlightService)
            .should()
            .getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        then(accommodationService)
            .should()
            .findLatestActive(
                limit
            );
    }

    @Test
    @DisplayName(
        "숙소 DB 조회가 실패하면 예외를 그대로 전달하고 "
            + "fallback DB를 다시 조회하지 않는다"
    )
    void DB_조회_실패는_fallback_대상이_아니다() {
        // given
        int limit = 2;
        DataAccessResourceFailureException databaseException =
            new DataAccessResourceFailureException(
                "Accommodation DB connection failed"
            );

        given(
            popularAccommodationSingleFlightService
                .getPopularAccommodations(
                    PopularAccommodationPeriod.DAILY,
                    limit
                )
        ).willThrow(databaseException);

        // when & then
        assertThatThrownBy(() ->
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            )
        ).isSameAs(databaseException);

        then(popularAccommodationSingleFlightService)
            .should()
            .getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );
        then(accommodationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "Redis 장애가 아닌 비즈니스 예외가 발생하면 "
            + "DB fallback을 실행하지 않는다"
    )
    void 비즈니스_예외가_발생하면_DB_fallback을_실행하지_않는다() {
        // given: 잘못된 limit 등의 비즈니스 예외를 준비합니다.
        int invalidLimit = 0;

        BusinessException exception =
            new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );

        given(
            popularAccommodationSingleFlightService
                .getPopularAccommodations(
                    PopularAccommodationPeriod.DAILY,
                    invalidLimit
                )
        ).willThrow(
            exception
        );

        // when & then: 입력 오류를 fallback으로 숨기지 않습니다.
        assertThatThrownBy(
            () ->
                accommodationFacade
                    .getPopularAccommodations(
                        PopularAccommodationPeriod.DAILY,
                        invalidLimit
                    )
        ).isSameAs(
            exception
        );

        then(popularAccommodationSingleFlightService)
            .should()
            .getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                invalidLimit
            );

        /*
         * Redis 장애가 아니므로
         * 최신 숙소 fallback 조회를 수행하지 않습니다.
         */
        then(accommodationService)
            .shouldHaveNoInteractions();
    }
}
