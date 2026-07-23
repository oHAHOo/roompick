package com.roompick.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 예약 날짜 검증과 활성 예약 중복 확인 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("겹치는 활성 예약이 없으면 객실을 예약할 수 있다")
    void roomIsAvailableWithoutOverlappingReservation() {
        // given
        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        given(
            reservationRepository.existsActiveOverlappingReservation(
                eq(1L),
                eq(checkInDate),
                eq(checkOutDate),
                any(LocalDateTime.class)
            )
        ).willReturn(false);

        // when
        boolean available = reservationService.isRoomAvailable(
            1L,
            checkInDate,
            checkOutDate
        );

        // then
        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("겹치는 활성 예약이 있으면 객실을 예약할 수 없다")
    void roomIsUnavailableWithOverlappingReservation() {
        // given
        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        given(
            reservationRepository.existsActiveOverlappingReservation(
                eq(1L),
                eq(checkInDate),
                eq(checkOutDate),
                any(LocalDateTime.class)
            )
        ).willReturn(true);

        // when
        boolean available = reservationService.isRoomAvailable(
            1L,
            checkInDate,
            checkOutDate
        );

        // then
        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("체크인 날짜가 과거이면 예약 가능 여부를 확인할 수 없다")
    void rejectPastCheckInDate() {
        // given
        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).minusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        // when
        BusinessException exception = catchThrowableOfType(
            () -> reservationService.isRoomAvailable(
                1L,
                checkInDate,
                checkOutDate
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_STAY_PERIOD);

        // 잘못된 날짜는 DB까지 조회하지 않습니다.
        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("체크인 날짜와 체크아웃 날짜가 같으면 조회할 수 없다")
    void rejectSameCheckInAndCheckOutDate() {
        // given
        LocalDate sameDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);

        // when
        BusinessException exception = catchThrowableOfType(
            () -> reservationService.isRoomAvailable(
                1L,
                sameDate,
                sameDate
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_STAY_PERIOD);

        verifyNoInteractions(reservationRepository);
    }
}
