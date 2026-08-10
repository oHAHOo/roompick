package com.roompick.domain.room.dto;

import java.util.List;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomImage;

/**
 * 객실 상세·예약 가능 여부 화면에 필요한
 * 객실 기본 정보를 반환하는 응답 DTO입니다.
 *
 * 숙소명과 주소는 화면에서 사용하지 않으므로 반환하지 않습니다.
 */
public record RoomDetailResponseDto(

    Long roomId,

    String roomNumber,

    String name,

    String description,

    long pricePerNight,

    int standardCapacity,

    int maxCapacity,

    List<String> imageUrls

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
            room.getImages().stream()
                .map(RoomImage::getImageUrl)
                .toList()
        );
    }
}
