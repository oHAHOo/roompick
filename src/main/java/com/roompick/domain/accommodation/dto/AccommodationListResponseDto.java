package com.roompick.domain.accommodation.dto;

/**
 * 전체 숙소 목록의 숙소 요약 정보를 반환하는 DTO입니다.
 *
 * W1 와이어프레임의 숙소 카드에 필요한
 * 최소한의 정보만 반환합니다.
 *
 * imageUrl은 등록된 이미지 중 대표(첫 번째) 이미지이며,
 * Repository 조회 쿼리의 LEFT JOIN으로 함께 채워집니다.
 */
public record AccommodationListResponseDto(

    Long accommodationId,

    String name,

    String address,

    String imageUrl

) {

    /**
     * 대표 이미지 조회가 필요 없는 테스트 등에서 사용합니다.
     */
    public AccommodationListResponseDto(
        Long accommodationId,
        String name,
        String address
    ) {
        this(
            accommodationId,
            name,
            address,
            null
        );
    }
}
