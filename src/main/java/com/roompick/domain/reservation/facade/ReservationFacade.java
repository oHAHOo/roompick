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

import lombok.RequiredArgsConstructor;

/**
 * 객실 도메인과 예약 도메인의
 * 예약 생성 흐름을 조율합니다.
 */
@Component
@RequiredArgsConstructor
public class ReservationFacade {

    private final RoomService roomService;
    private final ReservationService reservationService;
    private final ReservationIdempotencyService
        reservationIdempotencyService;

    /**
     * 인증된 회원의 예약을 멱등하게 생성합니다.
     *
     * 회원 ID와 멱등성 키를 기준으로 최초 처리 정보를
     * 생성하거나 기존 처리 결과를 조회합니다.
     *
     * 동일 키와 동일 요청이 이미 완료됐다면
     * 객실 락을 다시 획득하거나 새로운 예약을 생성하지 않고
     * 최초 요청으로 생성된 예약 결과를 반환합니다.
     *
     * 최초 요청이라면 객실에 비관적 쓰기 락을 획득한 뒤
     * 숙박 기간의 중복 예약을 검증하고 저장합니다.
     *
     * 멱등성 처리 정보 생성부터 예약 저장 및 완료 상태 변경까지
     * 하나의 트랜잭션으로 묶어 함께 커밋하거나 롤백합니다.
     */
    @Transactional
    public ReservationCreateResponseDto createReservation(
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

        /*
         * 같은 회원이 같은 키와 동일한 요청을
         * 다시 전달했다면 최초 예약 결과를 반환합니다.
         */
        if (idempotency.isCompleted()) {
            Reservation completedReservation =
                idempotency
                    .getCompletedReservation();

            return ReservationCreateResponseDto.from(
                completedReservation
            );
        }

        Room room =
            roomService
                .findReservableRoomForUpdate(
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
    public ReservationPageResponseDto getMyReservations(
        Long memberId,
        int page,
        int size
    ) {
        Page<Reservation> reservationPage =
            reservationService.findMyReservations(
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
    public ReservationDetailResponseDto getMyReservation(
        Long memberId,
        Long reservationId
    ) {
        Reservation reservation =
            reservationService.findMyReservation(
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
    public ReservationCancelResponseDto cancelReservation(
        Long memberId,
        Long reservationId
    ) {
        Reservation reservation =
            reservationService.cancelReservation(
                memberId,
                reservationId
            );

        return ReservationCancelResponseDto.from(
            reservation
        );
    }
}
