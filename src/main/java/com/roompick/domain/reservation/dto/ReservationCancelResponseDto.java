package com.roompick.domain.reservation.dto;

import java.time.LocalDateTime;

import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;

/**
 * 예약 취소 결과를 반환하는 DTO입니다.
 *
 * 취소된 예약의 식별자와 변경된 상태,
 * 실제 취소 처리 시각만 간단하게 반환합니다.
 */
public record ReservationCancelResponseDto(

    Long reservationId,

    ReservationStatus status,

    LocalDateTime canceledAt
) {

    /**
     * 취소 처리된 Reservation Entity를 응답 DTO로 변환합니다.
     */
    public static ReservationCancelResponseDto from(Reservation reservation) {
        return new ReservationCancelResponseDto(
            reservation.getId(),
            reservation.getStatus(),
            reservation.getCanceledAt()
        );
    }
}
