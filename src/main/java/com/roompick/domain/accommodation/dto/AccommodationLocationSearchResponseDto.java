package com.roompick.domain.accommodation.dto;

/**
 * 위치 기반 숙소 검색 결과를 반환하는 DTO입니다.
 *
 * distanceKm은 사용자가 전달한 위치와 숙소 사이의
 * 직선 거리를 킬로미터 단위로 나타냅니다.
 */
public record AccommodationLocationSearchResponseDto(
    Long accommodationId,
    String name,
    String address,
    double latitude,
    double longitude,
    double distanceKm,
    String imageUrl
) {
}
