package com.roompick.domain.accommodation.dto;

/**
 * 전체 숙소 목록의 숙소 요약 정보를 반환하는 DTO입니다.
 *
 * W1 와이어프레임의 숙소 카드에 필요한
 * 최소한의 정보만 반환합니다.
 *
 * imageUrl은 등록된 이미지 중 대표(첫 번째) 이미지이며,
 * Repository 조회 직후 Service에서 별도 배치 조회로 채워집니다.
 */
public record AccommodationListResponseDto(

    Long accommodationId,

    String name,

    String address,

    String imageUrl

) {

    /**
     * Repository의 DTO 직접 조회에서 사용합니다.
     *
     * 대표 이미지는 이 시점에 알 수 없으므로 imageUrl은 null로 설정합니다.
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

    /**
     * 배치 조회한 대표 이미지 URL을 채운 새 DTO를 반환합니다.
     */
    public AccommodationListResponseDto withImageUrl(String imageUrl) {
        return new AccommodationListResponseDto(
            accommodationId,
            name,
            address,
            imageUrl
        );
    }
}
