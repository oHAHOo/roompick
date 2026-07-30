package com.roompick.domain.reservation.dto;

import java.time.LocalDate;

import com.roompick.domain.reservation.validation.ValidStayPeriod;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 예약 생성 요청 정보를 전달하는 DTO입니다.
 *
 * 회원 ID는 요청 Body로 받지 않고
 * 로그인한 사용자의 인증 정보에서 가져옵니다.
 */
@ValidStayPeriod
public record ReservationCreateRequestDto(

    @NotNull(message = "객실 ID는 필수입니다.")
    Long roomId,

    @NotNull(message = "체크인 날짜는 필수입니다.")
    LocalDate checkInDate,

    @NotNull(message = "체크아웃 날짜는 필수입니다.")
    LocalDate checkOutDate,

    /*
     * Integer를 사용해야 요청에서 값이 누락됐을 때
     * null 여부를 구분하고 @NotNull 검증을 적용할 수 있습니다.
     */
    @NotNull(message = "예약 인원은 필수입니다.")
    @Min(value = 1, message = "예약 인원은 1명 이상이어야 합니다.")
    Integer guestCount
) {
}
