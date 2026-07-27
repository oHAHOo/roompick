package com.roompick.domain.accommodation.dto;

/**
 * 전체 숙소 목록의 숙소 요약 정보를 반환하는 DTO입니다.
 *
 * W1 와이어프레임의 숙소 카드에 필요한
 * 최소한의 정보만 반환합니다.
 */
public record AccommodationListResponseDto(

    Long accommodationId,

    String name,

    String address

) {
}
