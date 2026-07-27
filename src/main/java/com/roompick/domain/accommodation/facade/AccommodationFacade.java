package com.roompick.domain.accommodation.facade;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationPageResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccommodationFacade {

    private final AccommodationService accommodationService;
    private final RoomService roomService;

    /**
     * 운영 중인 숙소 목록 조회 흐름을 조율합니다.
     *
     * 페이지 요청값을 Service에 전달하고,
     * 조회 결과를 API 응답용 페이지 DTO로 변환합니다.
     */
    public AccommodationPageResponseDto getAccommodationList(
        int page,
        int size
    ) {
        Page<AccommodationListResponseDto> accommodationPage =
            accommodationService.findAllActive(
                page,
                size
            );

        return AccommodationPageResponseDto.from(
            accommodationPage
        );
    }

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

    /**
     * 운영 중인 숙소에 소속된 운영 중인 객실 목록을 조회합니다.
     *
     * 먼저 숙소의 존재 여부와 운영 상태를 확인한 뒤,
     * 객실 목록 화면에 필요한 정보만 조회합니다.
     */
    public List<RoomListResponseDto> getRoomList(
        Long accommodationId
    ) {
        accommodationService.findActiveById(
            accommodationId
        );

        return roomService
            .findAllActiveSummaryByAccommodationId(
                accommodationId
            );
    }
}
