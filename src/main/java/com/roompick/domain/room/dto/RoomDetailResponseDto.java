package com.roompick.domain.room.dto;

import java.util.List;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomImage;

/**
 * 객실 상세·예약 가능 여부 화면에 필요한
 * 객실 기본 정보를 반환하는 응답 DTO입니다.
 *
 * pricePerNight는 현재 적용 가격이고,
 * normalPricePerNight는 객실에 등록된 정상 가격입니다.
 */
public record RoomDetailResponseDto(

    Long roomId,

    String roomNumber,

    String name,

    String description,

    long pricePerNight,

    long normalPricePerNight,

    boolean discountApplied,

    int standardCapacity,

    int maxCapacity,

    List<String> imageUrls

) {

    /**
     * Room 엔티티와 현재 적용 가격을
     * 객실 상세 응답으로 변환합니다.
     */
    public static RoomDetailResponseDto from(
        Room room,
        long appliedPricePerNight
    ) {
        long normalPricePerNight =
            room.getPricePerNight();

        return new RoomDetailResponseDto(
            room.getId(),
            room.getRoomNumber(),
            room.getName(),
            room.getDescription(),
            appliedPricePerNight,
            normalPricePerNight,
            appliedPricePerNight
                < normalPricePerNight,
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            room.getImages().stream()
                .map(RoomImage::getImageUrl)
                .toList()
        );
    }
}
