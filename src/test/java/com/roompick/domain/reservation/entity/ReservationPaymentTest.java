package com.roompick.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.room.entity.Room;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentTest {

    @Mock
    private Member member;

    @Mock
    private Room room;

    private Reservation reservation;

    private LocalDateTime expiresAt;

    @BeforeEach
    void setUp() {
        given(member.getId())
            .willReturn(1L);

        given(room.getMaxCapacity())
            .willReturn(2);

        given(room.getPricePerNight())
            .willReturn(100_000L);

        expiresAt =
            LocalDateTime.of(
                2026,
                7,
                24,
                21,
                0
            );

        reservation =
            Reservation.create(
                member,
                room,
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 27),
                2,
                expiresAt
            );
    }

    @Test
    @DisplayName("결제 성공 시 PENDING_PAYMENT 예약을 CONFIRMED로 변경한다")
    void confirmReservationAfterPaymentSuccess() {
        // given
        LocalDateTime approvedAt =
            expiresAt.minusMinutes(10);

        // when
        reservation.confirmPayment(
            1L,
            approvedAt
        );

        // then
        assertThat(reservation.getStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );
    }

    @Test
    @DisplayName("다른 회원은 예약을 확정할 수 없다")
    void rejectConfirmationByOtherMember() {
        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> reservation.confirmPayment(
                    2L,
                    expiresAt.minusMinutes(10)
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_ACCESS_DENIED
            );

        assertThat(reservation.getStatus())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );
    }

    @Test
    @DisplayName("결제 대기 시간이 만료된 예약은 확정할 수 없다")
    void rejectConfirmationAfterExpiration() {
        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> reservation.confirmPayment(
                    1L,
                    expiresAt
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_PAYMENT_EXPIRED
            );

        assertThat(reservation.getStatus())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );
    }

    @Test
    @DisplayName("이미 확정된 예약은 다시 확정할 수 없다")
    void rejectDuplicatedReservationConfirmation() {
        // given
        reservation.confirmPayment(
            1L,
            expiresAt.minusMinutes(10)
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> reservation.confirmPayment(
                    1L,
                    expiresAt.minusMinutes(5)
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_NOT_PAYABLE
            );

        assertThat(reservation.getStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );
    }
}
