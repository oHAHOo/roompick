package com.roompick.domain.reservation.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.roompick.domain.reservation.entity.Reservation;

/**
 * 내 예약 목록의 페이지 정보를 반환하는 DTO입니다.
 *
 * 예약 목록과 현재 페이지 번호, 페이지 크기,
 * 전체 예약 수와 전체 페이지 수를 함께 반환합니다.
 */
public record ReservationPageResponseDto(

    List<ReservationListResponseDto> content,

    int pageNumber,

    int pageSize,

    long totalElements,

    int totalPages,

    boolean last

) {

    /**
     * Repository에서 조회한 예약 Page를
     * API 응답에 사용할 페이지 DTO로 변환합니다.
     */
    public static ReservationPageResponseDto from(
        Page<Reservation> reservationPage
    ) {
        List<ReservationListResponseDto> content =
            reservationPage
                .getContent()
                .stream()
                .map(ReservationListResponseDto::from)
                .toList();

        return new ReservationPageResponseDto(
            content,
            reservationPage.getNumber(),
            reservationPage.getSize(),
            reservationPage.getTotalElements(),
            reservationPage.getTotalPages(),
            reservationPage.isLast()
        );
    }
}
