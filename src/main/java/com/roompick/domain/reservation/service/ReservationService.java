package com.roompick.domain.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import com.roompick.domain.reservation.entity.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * 예약 가능 여부 확인과 예약 생성을 담당하는 Service입니다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    // 예약 생성 후 결제를 기다리는 시간입니다.
    private static final long PAYMENT_WAIT_MINUTES = 10L;

    private final ReservationRepository reservationRepository;
    private final EntityManager entityManager;

    /**
     * 요청한 숙박 기간에 객실을 예약할 수 있는지 확인합니다.
     */
    @Transactional(readOnly = true)
    public boolean isRoomAvailable(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        LocalDateTime now =
            LocalDateTime.now(SERVICE_ZONE_ID);

        validateStayPeriod(
            checkInDate,
            checkOutDate,
            now.toLocalDate()
        );

        return !existsActiveOverlappingReservation(
            roomId,
            checkInDate,
            checkOutDate,
            now
        );
    }

    /**
     * 결제 대기 상태의 새로운 예약을 생성합니다.
     *
     * 숙박 기간과 기존 활성 예약을 다시 확인한 뒤
     * 예약 당시의 객실 가격을 스냅샷으로 저장합니다.
     */
    @Transactional
    public Reservation createReservation(
        Long memberId,
        Room room,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount
    ) {
        validateMemberId(memberId);

        LocalDateTime now =
            LocalDateTime.now(SERVICE_ZONE_ID);

        validateStayPeriod(
            checkInDate,
            checkOutDate,
            now.toLocalDate()
        );

        validateRoomAvailable(
            room.getId(),
            checkInDate,
            checkOutDate,
            now
        );

        /*
         * 회원 정보를 다시 SELECT하지 않고
         * 인증된 회원 ID를 가진 JPA 참조 객체를 사용합니다.
         */
        Member memberReference =
            entityManager.getReference(
                Member.class,
                memberId
            );

        LocalDateTime expiresAt =
            now.plusMinutes(PAYMENT_WAIT_MINUTES);

        Reservation reservation = Reservation.create(
            memberReference,
            room,
            checkInDate,
            checkOutDate,
            guestCount,
            expiresAt
        );

        return reservationRepository.save(reservation);
    }

    /**
     * 인증된 회원이 생성한 예약 목록을 페이지 단위로 조회합니다.
     *
     * 예약 생성일이 최신인 순서로 조회하고,
     * 생성 시각이 같으면 예약 ID가 큰 순서로 정렬합니다.
     */
    @Transactional(readOnly = true)
    public Page<Reservation> findMyReservations(
        Long memberId,
        int page,
        int size
    ) {
        validateMemberId(memberId);
        validatePageRequest(page, size);

        Sort sort = Sort
            .by(
                Sort.Direction.DESC,
                "createdAt"
            )
            .and(
                Sort.by(
                    Sort.Direction.DESC,
                    "id"
                )
            );

        Pageable pageable = PageRequest.of(
            page,
            size,
            sort
        );

        return reservationRepository
            .findAllByMemberIdWithRoomAndAccommodation(
                memberId,
                pageable
            );
    }

    /**
     * 인증된 회원의 예약 상세 정보를 조회합니다.
     *
     * 예약이 존재하지 않으면 404를 반환하고,
     * 다른 회원의 예약이면 403을 반환합니다.
     */
    @Transactional(readOnly = true)
    public Reservation findMyReservation(
        Long memberId,
        Long reservationId
    ) {
        validateMemberId(memberId);
        validateReservationId(reservationId);

        Reservation reservation =
            reservationRepository
                .findByIdWithRoomAndAccommodation(
                    reservationId
                )
                .orElseThrow(() ->
                    new BusinessException(
                        ErrorCode.RESERVATION_NOT_FOUND
                    )
                );

        validateReservationOwner(
            reservation,
            memberId
        );

        return reservation;
    }

    /**
     * 같은 객실에 활성 상태로 겹치는 예약이 있는지 확인합니다.
     */
    private boolean existsActiveOverlappingReservation(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        LocalDateTime now
    ) {
        return reservationRepository
            .existsActiveOverlappingReservation(
                roomId,
                checkInDate,
                checkOutDate,
                now
            );
    }

    /**
     * 예약 생성 시 같은 기간의 활성 예약이 존재하면 거절합니다.
     */
    private void validateRoomAvailable(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        LocalDateTime now
    ) {
        boolean overlappingReservationExists =
            existsActiveOverlappingReservation(
                roomId,
                checkInDate,
                checkOutDate,
                now
            );

        if (overlappingReservationExists) {
            throw new BusinessException(
                ErrorCode.ROOM_NOT_AVAILABLE
            );
        }
    }

    /**
     * 인증된 회원 ID가 정상적으로 전달되었는지 확인합니다.
     */
    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(
                ErrorCode.UNAUTHORIZED
            );
        }
    }

    /**
     * 예약 ID를 기준으로 예약을 조회합니다.
     */
    @Transactional(readOnly = true)
    public Reservation findById(
        Long reservationId
    ) {
        return reservationRepository
            .findById(reservationId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.RESERVATION_NOT_FOUND
                )
            );
    }

    /**
     * 결제 준비가 가능한 예약을 조회하고 검증합니다.
     */
    @Transactional(readOnly = true)
    public Reservation findForPaymentPreparation(
        Long reservationId,
        Long memberId
    ) {
        Reservation reservation =
            reservationRepository
                .findById(reservationId)
                .orElseThrow(() ->
                    new BusinessException(
                        ErrorCode.RESERVATION_NOT_FOUND
                    )
                );

        LocalDateTime now =
            LocalDateTime.now(SERVICE_ZONE_ID);

        reservation.validatePaymentPreparation(
            memberId,
            now
        );

        return reservation;
    }

    /**
     * 체크인·체크아웃 날짜가 숙박 정책에 맞는지 검증합니다.
     */
    private void validateStayPeriod(
        LocalDate checkInDate,
        LocalDate checkOutDate,
        LocalDate today
    ) {
        if (
            checkInDate == null
                || checkOutDate == null
                || checkInDate.isBefore(today)
                || !checkInDate.isBefore(checkOutDate)
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_STAY_PERIOD
            );
        }
    }
}
