package com.roompick.domain.reservation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * 예약 생성 요청 정보를 전달하는 DTO입니다.
 *
 * 회원 ID는 요청 Body로 받지 않고
 * 로그인한 사용자의 인증 정보에서 가져옵니다.
 */
public record ReservationCreateRequestDto(

    @NotNull(message = "객실 ID는 필수입니다.")
    Long roomId,

    @NotNull(message = "체크인 날짜는 필수입니다.")
    LocalDate checkInDate,

    @NotNull(message = "체크아웃 날짜는 필수입니다.")
    LocalDate checkOutDate,

    int guestCount
) {
}
