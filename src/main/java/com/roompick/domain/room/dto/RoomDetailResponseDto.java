package com.roompick.domain.room.dto;

import com.roompick.domain.room.entity.Room;

/**
 * 객실 상세·예약 가능 여부 화면에 필요한
 * 객실 기본 정보를 반환하는 응답 DTO입니다.
 *
 * 숙소명과 주소는 화면에서 사용하지 않으므로 반환하지 않습니다.
 * 이미지 기능은 아직 구현되지 않아 imageUrl은 null로 반환합니다.
 */
public record RoomDetailResponseDto(

    Long roomId,

    String roomNumber,

    String name,

    String description,

    long pricePerNight,

    int standardCapacity,

    int maxCapacity,

    String imageUrl

) {

    /**
     * Room 엔티티를 객실 상세 응답 DTO로 변환합니다.
     */
    public static RoomDetailResponseDto from(
        Room room
    ) {
        return new RoomDetailResponseDto(
            room.getId(),
            room.getRoomNumber(),
            room.getName(),
            room.getDescription(),
            room.getPricePerNight(),
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            null
        );
    }
}
