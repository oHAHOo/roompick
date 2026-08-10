package com.roompick.domain.room.dto;

/**
 * 숙소별 객실 목록의 객실 요약 정보를 반환하는 DTO입니다.
 *
 * 와이어프레임의 객실 카드에 필요한
 * 최소한의 정보만 반환합니다.
 *
 * imageUrl은 등록된 이미지 중 대표(첫 번째) 이미지이며,
 * Repository 조회 직후 Service에서 별도 배치 조회로 채워집니다.
 */
public record RoomListResponseDto(

    Long roomId,

    String name,

    long pricePerNight,

    int standardCapacity,

    int maxCapacity,

    String imageUrl

) {

    /**
     * Repository의 DTO 직접 조회에서 사용합니다.
     *
     * 대표 이미지는 이 시점에 알 수 없으므로 imageUrl은 null로 설정합니다.
     */
    public RoomListResponseDto(
        Long roomId,
        String name,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity
    ) {
        this(
            roomId,
            name,
            pricePerNight,
            standardCapacity,
            maxCapacity,
            null
        );
    }

    /**
     * 배치 조회한 대표 이미지 URL을 채운 새 DTO를 반환합니다.
     */
    public RoomListResponseDto withImageUrl(String imageUrl) {
        return new RoomListResponseDto(
            roomId,
            name,
            pricePerNight,
            standardCapacity,
            maxCapacity,
            imageUrl
        );
    }
}
