package com.roompick.domain.reservation.facade;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.reservation.dto.ReservationCancelResponseDto;
import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.dto.ReservationDetailResponseDto;
import com.roompick.domain.reservation.dto.ReservationPageResponseDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationIdempotency;
import com.roompick.domain.reservation.service.ReservationIdempotencyService;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;
import com.roompick.domain.timesale.service.TimeSalePriceService;

import lombok.RequiredArgsConstructor;

/**
 * 객실·예약·타임세일 도메인의
 * 예약 생성 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class ReservationFacade {

    private final RoomService roomService;

    private final ReservationService
        reservationService;

    private final ReservationIdempotencyService
        reservationIdempotencyService;

    private final TimeSalePriceService
        timeSalePriceService;

    /**
     * 인증된 회원의 예약을 멱등하게 생성합니다.
     *
     * 최초 요청은 객실에 비관적 쓰기 락을 획득한 뒤
     * 현재 타임세일 가격을 계산하고 예약에 저장합니다.
     *
     * 동일 요청이 이미 완료됐다면 현재 가격을 다시 계산하지 않고
     * 최초 예약에 저장된 가격과 결과를 반환합니다.
     */
    @Transactional
    public ReservationCreateResponseDto
    createReservation(
        Long memberId,
        String idempotencyKey,
        ReservationCreateRequestDto request
    ) {
        ReservationIdempotency idempotency =
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    idempotencyKey,
                    request
                );

        if (idempotency.isCompleted()) {
            Reservation completedReservation =
                idempotency
                    .getCompletedReservation();

            return ReservationCreateResponseDto.from(
                completedReservation
            );
        }

        /*
         * 객실 행을 먼저 잠가 동일 객실의 예약 생성 요청을
         * 현재 트랜잭션 안에서 순차적으로 처리합니다.
         */
        Room room =
            roomService
                .findReservableRoomForUpdate(
                    request.roomId(),
                    request.guestCount()
                );

        /*
         * 객실 락을 획득한 후 현재 적용 가격을 계산합니다.
         */
        long appliedPricePerNight =
            timeSalePriceService
                .calculatePricePerNight(
                    room
                );

        Reservation reservation =
            reservationService
                .createReservation(
                    memberId,
                    room,
                    request.checkInDate(),
                    request.checkOutDate(),
                    request.guestCount(),
                    appliedPricePerNight
                );

        reservationIdempotencyService.complete(
            idempotency,
            reservation
        );

        return ReservationCreateResponseDto.from(
            reservation
        );
    }

    /**
     * 인증된 회원의 예약 목록을
     * 페이지 단위로 조회합니다.
     */
    public ReservationPageResponseDto
    getMyReservations(
        Long memberId,
        int page,
        int size
    ) {
        Page<Reservation> reservationPage =
            reservationService
                .findMyReservations(
                    memberId,
                    page,
                    size
                );

        return ReservationPageResponseDto.from(
            reservationPage
        );
    }

    /**
     * 인증된 회원의 예약 상세 정보를 조회합니다.
     */
    public ReservationDetailResponseDto
    getMyReservation(
        Long memberId,
        Long reservationId
    ) {
        Reservation reservation =
            reservationService
                .findMyReservation(
                    memberId,
                    reservationId
                );

        return ReservationDetailResponseDto.from(
            reservation
        );
    }

    /**
     * 인증된 회원의 결제 대기 예약을 취소합니다.
     */
    public ReservationCancelResponseDto
    cancelReservation(
        Long memberId,
        Long reservationId
    ) {
        Reservation reservation =
            reservationService
                .cancelReservation(
                    memberId,
                    reservationId
                );

        return ReservationCancelResponseDto.from(
            reservation
        );
    }
}
