package com.roompick.domain.admin.accommodation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
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
import com.roompick.domain.admin.accommodation.dto.request.AccommodationStatusUpdateRequestDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationStatusUpdateResponseDto;
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
                new BigDecimal("37.566500"),
                new BigDecimal("126.978000"),
                "15:00:00",
                "11:00:00"
            );

        Accommodation accommodation =
            Accommodation.create(
                request.name(),
                request.address(),
                request.description(),
                checkInTime,
                checkOutTime,
                request.latitude(),
                request.longitude()
            );

        given(
            accommodationService.createAccommodation(
                request.name(),
                request.address(),
                request.description(),
                checkInTime,
                checkOutTime,
                request.latitude(),
                request.longitude(),
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
                request.latitude(),
                request.longitude(),
                List.of()
            );
    }

    @Test
    @DisplayName("숙소와 소속 객실을 함께 논리 삭제한다")
    void 숙소와_소속_객실을_함께_논리_삭제한다() {
        Long accommodationId = 1L;

        adminAccommodationFacade.deleteAccommodation(
            accommodationId
        );

        then(accommodationService)
            .should()
            .inactivateAccommodation(accommodationId);
    }

    @Test
    @DisplayName("관리자 숙소 공개 요청을 처리한다")
    void 관리자_숙소_공개_요청을_처리한다() {
        // given
        Long accommodationId = 1L;
        AccommodationStatusUpdateRequestDto request =
            new AccommodationStatusUpdateRequestDto(AccommodationStatus.ACTIVE);

        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 중구",
                "테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        given(
            accommodationService.activateAccommodation(accommodationId)
        ).willReturn(accommodation);

        // when
        AccommodationStatusUpdateResponseDto response =
            adminAccommodationFacade.updateAccommodationStatus(
                accommodationId,
                request
            );

        // then
        assertThat(response.status())
            .isEqualTo(AccommodationStatus.ACTIVE);

        then(accommodationService)
            .should()
            .activateAccommodation(accommodationId);

        then(accommodationService)
            .should(org.mockito.Mockito.never())
            .inactivateAccommodation(accommodationId);
    }

    @Test
    @DisplayName("관리자 숙소 비공개 요청을 처리한다")
    void 관리자_숙소_비공개_요청을_처리한다() {
        // given
        Long accommodationId = 1L;
        AccommodationStatusUpdateRequestDto request =
            new AccommodationStatusUpdateRequestDto(AccommodationStatus.INACTIVE);

        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 중구",
                "테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );
        accommodation.inactivate();

        given(
            accommodationService.inactivateAccommodation(accommodationId)
        ).willReturn(accommodation);

        // when
        AccommodationStatusUpdateResponseDto response =
            adminAccommodationFacade.updateAccommodationStatus(
                accommodationId,
                request
            );

        // then
        assertThat(response.status())
            .isEqualTo(AccommodationStatus.INACTIVE);

        then(accommodationService)
            .should()
            .inactivateAccommodation(accommodationId);

        then(accommodationService)
            .should(org.mockito.Mockito.never())
            .activateAccommodation(accommodationId);
    }
}
