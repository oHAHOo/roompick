package com.roompick.domain.accommodation.dto;

/**
 * 전체 숙소 목록의 숙소 요약 정보를 반환하는 DTO입니다.
 *
 * W1 와이어프레임의 숙소 카드에 필요한
 * 최소한의 정보만 반환합니다.
 *
 * 이미지 기능은 아직 구현되지 않아 imageUrl은 null로 반환합니다.
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
     * 현재 이미지 데이터가 없으므로 imageUrl은 null로 설정합니다.
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
