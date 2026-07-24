package com.roompick.domain.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.room.entity.Room;

/**
 * 내 예약 목록의 예약 요약 정보를 반환하는 DTO입니다.
 *
 * 목록 조회에서는 예약 확인에 필요한 숙소·객실·숙박 기간·금액·상태만
 * 반환하고, 상세 정보는 예약 상세 조회 DTO에서 별도로 제공합니다.
 */
public record ReservationListResponseDto(

    Long reservationId,

    AccommodationSummaryDto accommodation,

    RoomSummaryDto room,

    LocalDate checkInDate,

    LocalDate checkOutDate,

    int guestCount,

    int nightCount,

    long totalAmount,

    ReservationStatus status,

    LocalDateTime expiresAt,

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
            AccommodationSummaryDto.from(
                room.getAccommodation()
            ),
            RoomSummaryDto.from(room),
            reservation.getCheckInDate(),
            reservation.getCheckOutDate(),
            reservation.getGuestCount(),
            reservation.getNightCount(),
            reservation.getTotalAmount(),
            reservation.getStatus(),
            reservation.getExpiresAt(),
            reservation.getCreatedAt()
        );
    }

    /**
     * 예약 목록에 포함할 숙소 요약 정보입니다.
     */
    public record AccommodationSummaryDto(

        Long accommodationId,

        String name

    ) {

        public static AccommodationSummaryDto from(
            Accommodation accommodation
        ) {
            return new AccommodationSummaryDto(
                accommodation.getId(),
                accommodation.getName()
            );
        }
    }

    /**
     * 예약 목록에 포함할 객실 요약 정보입니다.
     */
    public record RoomSummaryDto(

        Long roomId,

        String name,

        String roomNumber

    ) {

        public static RoomSummaryDto from(Room room) {
            return new RoomSummaryDto(
                room.getId(),
                room.getName(),
                room.getRoomNumber()
            );
        }
    }
}
