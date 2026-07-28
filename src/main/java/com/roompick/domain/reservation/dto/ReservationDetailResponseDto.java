package com.roompick.domain.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.room.entity.Room;

/**
 * 내 예약 상세 정보를 반환하는 DTO입니다.
 *
 * 예약 당시의 숙소·객실 정보와 숙박 기간, 가격 스냅샷,
 * 현재 예약 상태와 결제 대기·취소 시각을 반환합니다.
 */
public record ReservationDetailResponseDto(

    Long reservationId,

    AccommodationSummaryDto accommodation,

    RoomSummaryDto room,

    LocalDate checkInDate,

    LocalDate checkOutDate,

    int guestCount,

    int nightCount,

    long pricePerNight,

    long totalAmount,

    ReservationStatus status,

    LocalDateTime expiresAt,

    LocalDateTime canceledAt,

    LocalDateTime createdAt

) {

    /**
     * 조회된 예약 Entity를 상세 응답 DTO로 변환합니다.
     */
    public static ReservationDetailResponseDto from(
        Reservation reservation
    ) {
        Room room = reservation.getRoom();

        return new ReservationDetailResponseDto(
            reservation.getId(),
            AccommodationSummaryDto.from(
                room.getAccommodation()
            ),
            RoomSummaryDto.from(room),
            reservation.getCheckInDate(),
            reservation.getCheckOutDate(),
            reservation.getGuestCount(),
            reservation.getNightCount(),
            reservation.getPricePerNight(),
            reservation.getTotalAmount(),
            reservation.getStatus(),
            reservation.getExpiresAt(),
            reservation.getCanceledAt(),
            reservation.getCreatedAt()
        );
    }

    /**
     * 예약 상세 응답에 포함할 숙소 요약 정보입니다.
     */
    public record AccommodationSummaryDto(

        Long accommodationId,

        String name,

        String address

    ) {

        public static AccommodationSummaryDto from(
            Accommodation accommodation
        ) {
            return new AccommodationSummaryDto(
                accommodation.getId(),
                accommodation.getName(),
                accommodation.getAddress()
            );
        }
    }

    /**
     * 예약 상세 응답에 포함할 객실 요약 정보입니다.
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
