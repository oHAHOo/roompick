package com.roompick.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.room.entity.Room;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * Reservation의 생성 규칙과 가격 계산을 검증하는 단위 테스트입니다.
 */
class ReservationTest {

    @Test
    @DisplayName("정상적인 예약을 생성하면 숙박 일수와 총액을 계산한다")
    void createReservation() {
        // given
        Member member = createMember();
        Room room = createRoom();
        LocalDate checkInDate = LocalDate.of(2026, 8, 10);
        LocalDate checkOutDate = LocalDate.of(2026, 8, 12);
        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 14, 10);

        // when
        Reservation reservation = Reservation.create(
            member,
            room,
            checkInDate,
            checkOutDate,
            2,
            expiresAt
        );

        // then
        assertThat(reservation.getMember()).isEqualTo(member);
        assertThat(reservation.getRoom()).isEqualTo(room);
        assertThat(reservation.getCheckInDate()).isEqualTo(checkInDate);
        assertThat(reservation.getCheckOutDate()).isEqualTo(checkOutDate);
        assertThat(reservation.getGuestCount()).isEqualTo(2);
        assertThat(reservation.getNightCount()).isEqualTo(2);
        assertThat(reservation.getPricePerNight()).isEqualTo(100_000L);
        assertThat(reservation.getTotalAmount()).isEqualTo(200_000L);
        assertThat(reservation.getStatus())
            .isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reservation.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("체크인 날짜가 체크아웃 날짜보다 이전이 아니면 예약할 수 없다")
    void createReservationWithInvalidStayPeriod() {
        // given
        LocalDate sameDate = LocalDate.of(2026, 8, 10);

        // when
        BusinessException exception = catchThrowableOfType(
            () -> Reservation.create(
                createMember(),
                createRoom(),
                sameDate,
                sameDate,
                2,
                LocalDateTime.of(2026, 8, 1, 14, 10)
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_STAY_PERIOD);
    }

    @Test
    @DisplayName("객실 최대 인원을 초과하면 예약할 수 없다")
    void createReservationWithExceededCapacity() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Reservation.create(
                createMember(),
                createRoom(),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                3,
                LocalDateTime.of(2026, 8, 1, 14, 10)
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ROOM_CAPACITY_EXCEEDED);
    }

    private Member createMember() {
        return Member.create(
            "roompick@example.com",
            "encoded-password",
            "룸픽 회원"
        );
    }

    private Room createRoom() {
        Accommodation accommodation = Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구 테헤란로 123",
            "RoomPick MVP 예약 테스트를 위한 숙소입니다.",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );

        return Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸입니다.",
            100_000L,
            2,
            2
        );
    }
}
