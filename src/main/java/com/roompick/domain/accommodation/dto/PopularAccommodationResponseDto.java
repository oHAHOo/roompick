package com.roompick.domain.accommodation.dto;

/**
 * 일간 인기 숙소 목록의 숙소 한 건을 반환하는 DTO입니다.
 *
 * 기존 공개 숙소 요약 정보에 Redis 인기 순위를 추가합니다.
 */
public record PopularAccommodationResponseDto(

    int rank,

    Long accommodationId,

    String name,

    String address,

    String imageUrl

) {

    /**
     * 기존 숙소 요약 정보에 계산된 인기 순위를 결합합니다.
     *
     * 비공개 숙소 등을 제외한 최종 목록을 기준으로
     * rank를 다시 계산한 뒤 이 메서드를 호출합니다.
     */
    public static PopularAccommodationResponseDto from(
        int rank,
        AccommodationListResponseDto accommodation
    ) {
        return new PopularAccommodationResponseDto(
            rank,
            accommodation.accommodationId(),
            accommodation.name(),
            accommodation.address(),
            accommodation.imageUrl()
        );
    }
}
