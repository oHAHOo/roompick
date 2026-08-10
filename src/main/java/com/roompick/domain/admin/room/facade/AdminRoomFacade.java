package com.roompick.domain.admin.room.facade;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.room.dto.request.RoomCreateRequestDto;
import com.roompick.domain.admin.room.dto.request.RoomStatusUpdateRequestDto;
import com.roompick.domain.admin.room.dto.response.RoomCreateResponseDto;
import com.roompick.domain.admin.room.dto.response.RoomStatusUpdateResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;
import com.roompick.global.common.s3.ImageUploader;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminRoomFacade {

    private static final String IMAGE_DIRECTORY = "rooms";

    private final AccommodationService accommodationService;
    private final RoomService roomService;
    private final ImageUploader imageUploader;

    public RoomCreateResponseDto createRoom(
        Long accommodationId,
        RoomCreateRequestDto request,
        List<MultipartFile> images
    ) {
        Accommodation accommodation =
            accommodationService.findById(accommodationId);

        List<String> imageUrls =
            uploadImages(images);

        Room room = roomService.createRoom(
            accommodation,
            request.roomNumber(),
            request.name(),
            request.description(),
            request.pricePerNight(),
            request.standardCapacity(),
            request.maxCapacity(),
            imageUrls
        );

        return RoomCreateResponseDto.from(room);
    }

    private List<String> uploadImages(
        List<MultipartFile> images
    ) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return imageUploader.uploadAll(
            images,
            IMAGE_DIRECTORY
        );
    }

    public RoomStatusUpdateResponseDto updateRoomStatus(
        Long accommodationId,
        Long roomId,
        RoomStatusUpdateRequestDto request
    ) {
        Room room = switch (request.status()) {
            case ACTIVE -> roomService.activateRoom(
                accommodationId,
                roomId
            );
            case INACTIVE -> roomService.deactivateRoom(
                accommodationId,
                roomId
            );
        };

        return RoomStatusUpdateResponseDto.from(room);
    }
}
