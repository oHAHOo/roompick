package com.roompick.domain.travelplan.dto;

import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;

/**
 * 여행 계획에 맞춰 추천된 숙소를 반환하는 DTO입니다.
 *
 * 숙소 정보는 LLM 응답 텍스트가 아니라 DB 조회 결과에서 가져오고,
 * reason만 LLM이 생성한 추천 이유입니다.
 */
public record RecommendedAccommodationDto(
    Long accommodationId,
    String name,
    String address,
    double distanceKm,
    String imageUrl,
    String reason
) {

    public static RecommendedAccommodationDto from(
        AccommodationLocationSearchResponseDto accommodation,
        String reason
    ) {
        return new RecommendedAccommodationDto(
            accommodation.accommodationId(),
            accommodation.name(),
            accommodation.address(),
            accommodation.distanceKm(),
            accommodation.imageUrl(),
            reason
        );
    }
}
