package com.roompick.domain.room.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 객실 예약 가능 여부 조회에 필요한 날짜와 인원을 전달합니다.
 */
public record RoomAvailabilityRequestDto(

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate checkInDate,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate checkOutDate,

    int guestCount

) {
}
