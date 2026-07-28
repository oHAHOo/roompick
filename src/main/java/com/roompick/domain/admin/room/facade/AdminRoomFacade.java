package com.roompick.domain.admin.room.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.room.dto.request.RoomCreateRequestDto;
import com.roompick.domain.admin.room.dto.response.RoomCreateResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminRoomFacade {

    private final AccommodationService accommodationService;
    private final RoomService roomService;

    public RoomCreateResponseDto createRoom(
        Long accommodationId,
        RoomCreateRequestDto request
    ) {
        Accommodation accommodation =
            accommodationService.findById(accommodationId);

        Room room = roomService.createRoom(
            accommodation,
            request.roomNumber(),
            request.name(),
            request.description(),
            request.pricePerNight(),
            request.standardCapacity(),
            request.maxCapacity()
        );

        return RoomCreateResponseDto.from(room);
    }
}
