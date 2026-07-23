package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AccommodationServiceTest {

    @Mock
    private AccommodationRepository accommodationRepository;

    @InjectMocks
    private AccommodationService accommodationService;

    @Test
    void 숙소_조회에_성공한다() {
        // given: 조회할 숙소가 존재합니다.
        Long accommodationId = 1L;
        Accommodation accommodation = createAccommodation();

        given(accommodationRepository.findById(accommodationId))
            .willReturn(Optional.of(accommodation));

        // when: 숙소 ID로 조회합니다.
        Accommodation result =
            accommodationService.findById(accommodationId);

        // then: Repository에서 조회한 숙소가 반환됩니다.
        assertThat(result).isSameAs(accommodation);
    }

    @Test
    void 존재하지_않는_숙소를_조회하면_예외가_발생한다() {
        // given: 해당 ID의 숙소가 존재하지 않습니다.
        Long accommodationId = 999L;

        given(accommodationRepository.findById(accommodationId))
            .willReturn(Optional.empty());

        // when & then: 숙소 없음 공통 예외가 발생합니다.
        assertThatThrownBy(
            () -> accommodationService.findById(accommodationId)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ACCOMMODATION_NOT_FOUND);
    }

    private Accommodation createAccommodation() {
        return Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구",
            "RoomPick 테스트 숙소",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );
    }
}
