package com.roompick.domain.accommodation.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccommodationFacade {

    private final AccommodationService accommodationService;
    private final RoomService roomService;

    /**
     * 숙소 기본 정보와 소속 객실 목록을 함께 조회합니다.
     *
     * 각 도메인의 Service를 직접 연결하지 않고
     * Facade에서 전체 조회 흐름을 조율합니다.
     */
    public AccommodationDetailResponseDto getAccommodationDetail(Long accommodationId) {
        Accommodation accommodation = accommodationService.findById(accommodationId);

        List<Room> rooms = roomService.findAllByAccommodationId(accommodationId);

        return AccommodationDetailResponseDto.of(accommodation, rooms);
    }
}
