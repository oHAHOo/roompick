package com.roompick.domain.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.room.entity.Room;

/**
 * 내 예약 목록의 예약 요약 정보를 반환하는 DTO입니다.
 *
 * 목록 화면에 필요한 숙소명, 객실명, 숙박 기간,
 * 결제 금액과 예약 상태를 평탄한 구조로 반환합니다.
 */
public record ReservationListResponseDto(

    Long reservationId,

    String accommodationName,

    String roomName,

    LocalDate checkInDate,

    LocalDate checkOutDate,

    int guestCount,

    long totalAmount,

    ReservationStatus status,

    LocalDateTime createdAt

) {

    /**
     * 조회된 예약 Entity를 목록 응답 DTO로 변환합니다.
     */
    public static ReservationListResponseDto from(
        Reservation reservation
    ) {
        Room room = reservation.getRoom();

        return new ReservationListResponseDto(
            reservation.getId(),
            room.getAccommodation().getName(),
            room.getName(),
            reservation.getCheckInDate(),
            reservation.getCheckOutDate(),
            reservation.getGuestCount(),
            reservation.getTotalAmount(),
            reservation.getStatus(),
            reservation.getCreatedAt()
        );
    }
}
