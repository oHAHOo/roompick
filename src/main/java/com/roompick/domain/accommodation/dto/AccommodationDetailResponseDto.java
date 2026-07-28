package com.roompick.domain.accommodation.dto;

import java.time.LocalTime;

import com.roompick.domain.accommodation.entity.Accommodation;

/**
 * 숙소 상세 화면에 필요한 숙소 기본 정보를 반환하는 응답 DTO입니다.
 *
 * 객실 목록은 숙소별 객실 목록 조회 API에서 별도로 반환하므로
 * 숙소 상세 응답에는 포함하지 않습니다.
 */
public record AccommodationDetailResponseDto(

    Long accommodationId,

    String name,

    String address,

    String description,

    LocalTime checkInTime,

    LocalTime checkOutTime

) {

    /**
     * Accommodation 엔티티를 숙소 상세 응답 DTO로 변환합니다.
     */
    public static AccommodationDetailResponseDto from(
        Accommodation accommodation
    ) {
        return new AccommodationDetailResponseDto(
            accommodation.getId(),
            accommodation.getName(),
            accommodation.getAddress(),
            accommodation.getDescription(),
            accommodation.getCheckInTime(),
            accommodation.getCheckOutTime()
        );
    }
}
