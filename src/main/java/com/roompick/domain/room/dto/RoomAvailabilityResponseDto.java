package com.roompick.domain.room.dto;

import java.time.LocalDate;

import com.roompick.domain.reservation.vo.ReservationPrice;
import com.roompick.domain.room.entity.Room;

/**
 * 객실 예약 가능 여부와 예상 결제 금액을 반환합니다.
 */
public record RoomAvailabilityResponseDto(

    Long roomId,

    LocalDate checkInDate,

    LocalDate checkOutDate,

    int guestCount,

    int nightCount,

    long pricePerNight,

    long normalPricePerNight,

    boolean discountApplied,

    long totalAmount,

    RoomAvailabilityStatus status,

    boolean available,

    String unavailableReason

) {

    private static final String
        OVERLAPPING_RESERVATION_REASON =
        "선택한 날짜에 이미 예약된 객실입니다.";

    /**
     * 검증된 객실과 현재 적용 가격으로
     * 예약 가능 여부 응답을 생성합니다.
     */
    public static RoomAvailabilityResponseDto of(
        Room room,
        RoomAvailabilityRequestDto request,
        boolean available,
        long appliedPricePerNight
    ) {
        ReservationPrice reservationPrice =
            ReservationPrice.calculate(
                request.checkInDate(),
                request.checkOutDate(),
                appliedPricePerNight
            );

        String unavailableReason = available
            ? null
            : OVERLAPPING_RESERVATION_REASON;

        RoomAvailabilityStatus status = available
            ? RoomAvailabilityStatus.ACTIVE
            : RoomAvailabilityStatus.SOLD_OUT;

        long normalPricePerNight =
            room.getPricePerNight();

        return new RoomAvailabilityResponseDto(
            room.getId(),
            request.checkInDate(),
            request.checkOutDate(),
            request.guestCount(),
            reservationPrice.nightCount(),
            reservationPrice.pricePerNight(),
            normalPricePerNight,
            appliedPricePerNight
                < normalPricePerNight,
            reservationPrice.totalAmount(),
            status,
            available,
            unavailableReason
        );
    }
}
