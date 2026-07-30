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

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationService;
import com.roompick.domain.room.service.RoomService;

/**
 * 사용자 숙소 조회 흐름과 인기 숙소 점수 기록 여부를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationFacadeTest {

    @Mock
    private AccommodationService accommodationService;

    @Mock
    private RoomService roomService;

    @Mock
    private PopularAccommodationService popularAccommodationService;

    @InjectMocks
    private AccommodationFacade accommodationFacade;

    @Test
    @DisplayName("운영 중인 숙소 상세 조회에 성공하면 인기 점수를 기록한다")
    void 운영_중인_숙소_상세_조회에_성공하면_인기_점수를_기록한다() {
        // given
        Long accommodationId = 1L;

        LocalTime checkInTime = LocalTime.of(
            15,
            0
        );

        LocalTime checkOutTime = LocalTime.of(
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

        // when
        AccommodationDetailResponseDto response =
            accommodationFacade.getAccommodationDetail(
                accommodationId
            );

        // then
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
            .findActiveById(
                accommodationId
            );

        then(popularAccommodationService)
            .should()
            .recordView(
                accommodationId
            );
    }

    @Test
    @DisplayName("숙소 상세 조회에 실패하면 인기 점수를 기록하지 않는다")
    void 숙소_상세_조회에_실패하면_인기_점수를_기록하지_않는다() {
        // given
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

        // when & then
        assertThatThrownBy(
            () -> accommodationFacade.getAccommodationDetail(
                accommodationId
            )
        ).isSameAs(
            exception
        );

        then(popularAccommodationService)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인기 숙소를 Redis 순서로 정렬하고 제외된 숙소 이후 순위를 다시 계산한다")
    void 인기_숙소를_Redis_순서로_정렬하고_순위를_다시_계산한다() {
        // given
        int limit = 10;

        /*
         * Redis 인기 순위는 3번 → 2번 → 1번 숙소입니다.
         */
        List<Long> rankedAccommodationIds =
            List.of(
                3L,
                2L,
                1L
            );

        /*
         * DB에서는 ACTIVE 숙소만 반환합니다.
         *
         * 2번 숙소는 삭제됐거나 INACTIVE 상태라고 가정하여
         * DB 조회 결과에 포함하지 않습니다.
         *
         * 또한 DB 반환 순서를 1번 → 3번으로 구성해
         * Facade가 Redis 순서로 다시 정렬하는지 확인합니다.
         */
        List<AccommodationListResponseDto> activeAccommodations =
            List.of(
                new AccommodationListResponseDto(
                    1L,
                    "룸픽 서울 호텔",
                    "서울특별시 중구"
                ),
                new AccommodationListResponseDto(
                    3L,
                    "룸픽 부산 호텔",
                    "부산광역시 해운대구"
                )
            );

        given(
            popularAccommodationService.findRankedAccommodationIds(
                limit
            )
        ).willReturn(
            rankedAccommodationIds
        );

        given(
            accommodationService.findAllActiveSummaryByIds(
                rankedAccommodationIds
            )
        ).willReturn(
            activeAccommodations
        );

        // when
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                limit
            );

        // then
        assertThat(result).hasSize(
            2
        );

        /*
         * Redis 1위였던 3번 숙소가 최종 1위로 유지됩니다.
         */
        assertThat(result.get(0).rank())
            .isEqualTo(1);

        assertThat(result.get(0).accommodationId())
            .isEqualTo(3L);

        assertThat(result.get(0).name())
            .isEqualTo("룸픽 부산 호텔");

        /*
         * Redis 2위였던 2번 숙소가 제외됐으므로
         * 기존 3위였던 1번 숙소가 최종 2위가 됩니다.
         */
        assertThat(result.get(1).rank())
            .isEqualTo(2);

        assertThat(result.get(1).accommodationId())
            .isEqualTo(1L);

        assertThat(result.get(1).name())
            .isEqualTo("룸픽 서울 호텔");

        then(popularAccommodationService)
            .should()
            .findRankedAccommodationIds(
                limit
            );

        then(accommodationService)
            .should()
            .findAllActiveSummaryByIds(
                rankedAccommodationIds
            );
    }

    @Test
    @DisplayName("상위 랭킹에 비공개 숙소가 있어도 하위 ACTIVE 숙소로 limit을 채운다")
    void 상위_랭킹에_비공개_숙소가_있어도_하위_ACTIVE_숙소로_limit을_채운다() {
        // given
        int limit = 2;

        /*
         * Redis 인기 순위는 4번 → 3번 → 2번 → 1번입니다.
         *
         * 상위 2개 중 3번 숙소는 삭제됐거나 INACTIVE 상태이며,
         * 그 아래 순위인 2번 숙소가 ACTIVE 상태라고 가정합니다.
         */
        List<Long> rankedAccommodationIds =
            List.of(
                4L,
                3L,
                2L,
                1L
            );

        /*
         * DB는 ACTIVE 숙소만 반환합니다.
         * 반환 순서는 Redis 순서와 다르게 구성합니다.
         */
        List<AccommodationListResponseDto> activeAccommodations =
            List.of(
                new AccommodationListResponseDto(
                    1L,
                    "룸픽 제주 호텔",
                    "제주특별자치도 제주시"
                ),
                new AccommodationListResponseDto(
                    2L,
                    "룸픽 서울 호텔",
                    "서울특별시 중구"
                ),
                new AccommodationListResponseDto(
                    4L,
                    "룸픽 부산 호텔",
                    "부산광역시 해운대구"
                )
            );

        given(
            popularAccommodationService.findRankedAccommodationIds(
                limit
            )
        ).willReturn(
            rankedAccommodationIds
        );

        given(
            accommodationService.findAllActiveSummaryByIds(
                rankedAccommodationIds
            )
        ).willReturn(
            activeAccommodations
        );

        // when
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                limit
            );

        // then
        assertThat(result).hasSize(
            limit
        );

        /*
         * Redis 1위인 4번 숙소는 최종 1위입니다.
         */
        assertThat(result.get(0).rank())
            .isEqualTo(1);

        assertThat(result.get(0).accommodationId())
            .isEqualTo(4L);

        /*
         * Redis 2위인 3번 숙소가 제외되므로
         * Redis 3위인 2번 숙소가 최종 2위를 채웁니다.
         */
        assertThat(result.get(1).rank())
            .isEqualTo(2);

        assertThat(result.get(1).accommodationId())
            .isEqualTo(2L);

        /*
         * limit이 채워진 뒤에는 더 낮은 순위의
         * 1번 ACTIVE 숙소를 결과에 포함하지 않습니다.
         */
        assertThat(result)
            .extracting(
                PopularAccommodationResponseDto::accommodationId
            )
            .containsExactly(
                4L,
                2L
            );

        then(popularAccommodationService)
            .should()
            .findRankedAccommodationIds(
                limit
            );

        then(accommodationService)
            .should()
            .findAllActiveSummaryByIds(
                rankedAccommodationIds
            );
    }

    @Test
    @DisplayName("인기 숙소 랭킹이 없으면 빈 목록을 반환한다")
    void 인기_숙소_랭킹이_없으면_빈_목록을_반환한다() {
        // given
        int limit = 10;

        List<Long> rankedAccommodationIds =
            List.of();

        given(
            popularAccommodationService.findRankedAccommodationIds(
                limit
            )
        ).willReturn(
            rankedAccommodationIds
        );

        given(
            accommodationService.findAllActiveSummaryByIds(
                rankedAccommodationIds
            )
        ).willReturn(
            List.of()
        );

        // when
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                limit
            );

        // then
        assertThat(result).isEmpty();

        then(popularAccommodationService)
            .should()
            .findRankedAccommodationIds(
                limit
            );

        then(accommodationService)
            .should()
            .findAllActiveSummaryByIds(
                rankedAccommodationIds
            );
    }
}
