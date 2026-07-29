package com.roompick.domain.room.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.dto.RoomAvailabilityRequestDto;
import com.roompick.domain.room.dto.RoomAvailabilityResponseDto;
import com.roompick.domain.room.dto.RoomDetailResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

/**
 * 객실 도메인과 예약 도메인의 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class RoomFacade {

    private final RoomService roomService;
    private final ReservationService reservationService;

    /**
     * 객실 상세 정보를 조회하고 공개 응답으로 변환합니다.
     */
    public RoomDetailResponseDto getRoomDetail(Long roomId) {
        Room room = roomService.findActiveById(roomId);

        return RoomDetailResponseDto.from(room);
    }

    /**
     * 객실 상태·인원·숙박 기간·기존 예약을 확인하고
     * 예약 가능 여부와 예상 결제 금액을 반환합니다.
     */
    public RoomAvailabilityResponseDto getRoomAvailability(
        Long roomId,
        RoomAvailabilityRequestDto request
    ) {
        Room room = roomService.findReservableRoom(
            roomId,
            request.guestCount()
        );

        boolean available = reservationService.isRoomAvailable(
            roomId,
            request.checkInDate(),
            request.checkOutDate()
        );

        return RoomAvailabilityResponseDto.of(
            room,
            request,
            available
        );
    }
}
