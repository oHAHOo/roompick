package com.roompick.domain.admin.room.dto.response;

import java.util.List;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomImage;
import com.roompick.domain.room.entity.RoomStatus;

public record RoomCreateResponseDto(

    Long roomId,

    Long accommodationId,

    String roomNumber,

    String name,

    String description,

    long pricePerNight,

    int standardCapacity,

    int maxCapacity,

    RoomStatus status,

    List<String> imageUrls

) {

    public static RoomCreateResponseDto from(Room room) {
        return new RoomCreateResponseDto(
            room.getId(),
            room.getAccommodation().getId(),
            room.getRoomNumber(),
            room.getName(),
            room.getDescription(),
            room.getPricePerNight(),
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            room.getStatus(),
            room.getImages().stream()
                .map(RoomImage::getImageUrl)
                .toList()
        );
    }
}
