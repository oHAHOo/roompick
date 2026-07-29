package com.roompick.domain.admin.room.dto.request;

import com.roompick.domain.room.entity.RoomStatus;

import jakarta.validation.constraints.NotNull;

public record RoomStatusUpdateRequestDto(

    @NotNull RoomStatus status

) {
}
