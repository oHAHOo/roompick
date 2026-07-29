package com.roompick.domain.admin.room.dto.response;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;

public record RoomStatusUpdateResponseDto(

    Long roomId,

    RoomStatus status

) {

    public static RoomStatusUpdateResponseDto from(Room room) {
        return new RoomStatusUpdateResponseDto(
            room.getId(),
            room.getStatus()
        );
    }
}
