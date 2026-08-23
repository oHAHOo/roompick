package com.roompick.domain.room.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.dto.RoomAvailabilityRequestDto;
import com.roompick.domain.room.dto.RoomAvailabilityResponseDto;
import com.roompick.domain.room.dto.RoomDetailResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;
import com.roompick.domain.timesale.service.TimeSalePriceService;

import lombok.RequiredArgsConstructor;

/**
 * 객실 도메인과 예약 도메인의 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class RoomFacade {

    private final RoomService roomService;

    private final ReservationService
        reservationService;

    private final TimeSalePriceService
        timeSalePriceService;

    /**
     * 객실 상세 정보와 현재 적용 가격을 조회합니다.
     *
     * 관리자는 INACTIVE 객실도 조회할 수 있고,
     * 응답에 운영 상태가 포함됩니다.
     */
    public RoomDetailResponseDto getRoomDetail(
        Long roomId,
        boolean admin
    ) {
        Room room =
            admin
                ? roomService.findAnyByIdForAdmin(roomId)
                : roomService.findActiveById(roomId);

        long appliedPricePerNight =
            timeSalePriceService
                .calculatePricePerNight(
                    room
                );

        return admin
            ? RoomDetailResponseDto.forAdmin(room, appliedPricePerNight)
            : RoomDetailResponseDto.from(room, appliedPricePerNight);
    }

    /**
     * 객실 상태·인원·숙박 기간·기존 예약을 확인하고
     * 타임세일이 반영된 예상 결제 금액을 반환합니다.
     */
    public RoomAvailabilityResponseDto
    getRoomAvailability(
        Long roomId,
        RoomAvailabilityRequestDto request
    ) {
        Room room =
            roomService.findReservableRoom(
                roomId,
                request.guestCount()
            );

        boolean available =
            reservationService.isRoomAvailable(
                roomId,
                request.checkInDate(),
                request.checkOutDate()
            );

        long appliedPricePerNight =
            timeSalePriceService
                .calculatePricePerNight(
                    room
                );

        return RoomAvailabilityResponseDto.of(
            room,
            request,
            available,
            appliedPricePerNight
        );
    }
}
