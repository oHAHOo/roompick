package com.roompick.domain.admin.accommodation.facade;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.accommodation.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;
import com.roompick.global.common.s3.ImageUploader;

@ExtendWith(MockitoExtension.class)
class AdminAccommodationFacadeTest {

    @Mock
    private AccommodationService accommodationService;

    @Mock
    private ImageUploader imageUploader;

    @InjectMocks
    private AdminAccommodationFacade adminAccommodationFacade;

    @Test
    @DisplayName("관리자 숙소 등록 요청을 처리한다")
    void 관리자_숙소_등록_요청을_처리한다() {
        // given
        LocalTime checkInTime = LocalTime.of(15, 0, 0);
        LocalTime checkOutTime = LocalTime.of(11, 0, 0);

        AccommodationCreateRequestDto request =
            new AccommodationCreateRequestDto(
                "룸픽 호텔",
                "서울특별시 중구",
                "RoomPick MVP 예약 테스트를 위한 숙소",
                "15:00:00",
                "11:00:00"
            );

        Accommodation accommodation =
            Accommodation.create(
                request.name(),
                request.address(),
                request.description(),
                checkInTime,
                checkOutTime
            );

        given(
            accommodationService.createAccommodation(
                request.name(),
                request.address(),
                request.description(),
                checkInTime,
                checkOutTime,
                List.of()
            )
        ).willReturn(accommodation);

        // when
        AccommodationCreateResponseDto response =
            adminAccommodationFacade.createAccommodation(request, null);

        // then
        assertThat(response.name())
            .isEqualTo(request.name());

        assertThat(response.address())
            .isEqualTo(request.address());

        assertThat(response.description())
            .isEqualTo(request.description());

        assertThat(response.checkInTime())
            .isEqualTo(checkInTime);

        assertThat(response.checkOutTime())
            .isEqualTo(checkOutTime);

        assertThat(response.status())
            .isEqualTo(AccommodationStatus.ACTIVE);

        then(accommodationService)
            .should()
            .createAccommodation(
                request.name(),
                request.address(),
                request.description(),
                checkInTime,
                checkOutTime,
                List.of()
            );
    }
}
