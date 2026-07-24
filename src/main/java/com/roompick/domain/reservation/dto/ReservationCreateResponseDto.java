package com.roompick.domain.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.room.entity.Room;

/**
 * 예약 생성 결과를 반환하는 DTO입니다.
 *
 * 예약 당시의 숙소·객실 정보와 가격 스냅샷,
 * 결제 대기 만료 시각을 함께 반환합니다.
 */
public record ReservationCreateResponseDto(

    Long reservationId,

    Long memberId,

    AccommodationSummaryDto accommodation,

    RoomSummaryDto room,

    LocalDate checkInDate,

    LocalDate checkOutDate,

    int guestCount,

    int nightCount,

    long pricePerNight,

    long totalAmount,

    ReservationStatus status,

    LocalDateTime expiresAt

) {

    /**
     * 저장된 예약 Entity를 예약 생성 응답으로 변환합니다.
     */
    public static ReservationCreateResponseDto from(
        Reservation reservation
    ) {
        Room room = reservation.getRoom();

        return new ReservationCreateResponseDto(
            reservation.getId(),
            reservation.getMember().getId(),
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
            reservation.getExpiresAt()
        );
    }

    /**
     * 예약 응답에 포함할 숙소 요약 정보입니다.
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
     * 예약 응답에 포함할 객실 요약 정보입니다.
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
