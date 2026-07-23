package com.roompick.domain.admin.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.dto.response.AccommodationCreateResponseDto;

@ExtendWith(MockitoExtension.class)
class AdminAccommodationFacadeTest {

    @Mock
    private AccommodationService accommodationService;

    @InjectMocks
    private AdminAccommodationFacade adminAccommodationFacade;

    @Test
    @DisplayName("관리자 숙소 등록 유스케이스에 성공한다")
    void 관리자_숙소_등록_유스케이스에_성공한다() {
        // given
        LocalTime checkInTime = LocalTime.of(15, 0);
        LocalTime checkOutTime = LocalTime.of(11, 0);

        AccommodationCreateRequestDto request =
            new AccommodationCreateRequestDto(
                "룸픽 호텔",
                "서울특별시 중구",
                "RoomPick MVP 예약 테스트를 위한 숙소",
                checkInTime,
                checkOutTime
            );

        Accommodation accommodation = Accommodation.create(
            request.name(),
            request.address(),
            request.description(),
            request.checkInTime(),
            request.checkOutTime()
        );

        given(
            accommodationService.createAccommodation(
                request.name(),
                request.address(),
                request.description(),
                request.checkInTime(),
                request.checkOutTime()
            )
        ).willReturn(accommodation);

        // when
        AccommodationCreateResponseDto result =
            adminAccommodationFacade.createAccommodation(request);

        // then
        assertThat(result.name()).isEqualTo("룸픽 호텔");
        assertThat(result.address()).isEqualTo("서울특별시 중구");
        assertThat(result.checkInTime()).isEqualTo(checkInTime);
        assertThat(result.checkOutTime()).isEqualTo(checkOutTime);
        assertThat(result.status()).isEqualTo(AccommodationStatus.ACTIVE);

        verify(accommodationService).createAccommodation(
            request.name(),
            request.address(),
            request.description(),
            request.checkInTime(),
            request.checkOutTime()
        );
    }
}
