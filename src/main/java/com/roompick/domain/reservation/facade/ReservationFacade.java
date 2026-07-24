package com.roompick.domain.reservation.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

/**
 * 객실 도메인과 예약 도메인의 예약 생성 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class ReservationFacade {

    private final RoomService roomService;
    private final ReservationService reservationService;

    /**
     * 인증된 회원의 예약을 생성합니다.
     *
     * 객실 상태와 인원을 확인한 뒤,
     * 숙박 기간의 중복 예약을 검증하고 예약을 저장합니다.
     */
    public ReservationCreateResponseDto createReservation(Long memberId, ReservationCreateRequestDto request) {
        /*
         * 예약 생성 응답에 숙소 정보가 필요하므로
         * 객실과 숙소를 한 번의 쿼리로 조회합니다.
         */
        Room room =
            roomService.findReservableRoomWithAccommodation(
                request.roomId(),
                request.guestCount()
            );

        Reservation reservation =
            reservationService.createReservation(
                memberId,
                room,
                request.checkInDate(),
                request.checkOutDate(),
                request.guestCount()
            );

        return ReservationCreateResponseDto.from(reservation);
    }
}
