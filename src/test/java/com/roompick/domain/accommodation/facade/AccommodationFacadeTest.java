package com.roompick.domain.accommodation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
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
}
