package com.roompick.domain.room.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
    long totalAmount,
    boolean available,
    String unavailableReason

) {

    private static final String OVERLAPPING_RESERVATION_REASON = "선택한 날짜에 이미 예약된 객실입니다.";

    /**
     * 검증된 객실과 요청 정보로 예약 가능 여부 응답을 생성합니다.
     */
    public static RoomAvailabilityResponseDto of(
        Room room,
        RoomAvailabilityRequestDto request,
        boolean available
    ) {
        int nightCount = Math.toIntExact(
            ChronoUnit.DAYS.between(
                request.checkInDate(),
                request.checkOutDate()
            )
        );

        long totalAmount = Math.multiplyExact(
            room.getPricePerNight(),
            nightCount
        );

        String unavailableReason = available
            ? null
            : OVERLAPPING_RESERVATION_REASON;

        return new RoomAvailabilityResponseDto(
            room.getId(),
            request.checkInDate(),
            request.checkOutDate(),
            request.guestCount(),
            nightCount,
            room.getPricePerNight(),
            totalAmount,
            available,
            unavailableReason
        );
    }
}
