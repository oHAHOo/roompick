package com.roompick.domain.reservation.service;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationIdempotency;
import com.roompick.domain.reservation.repository.ReservationIdempotencyRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 예약 생성 요청의 멱등성 처리 흐름을 담당합니다.
 *
 * 최초 처리 정보 생성, 동일 키 요청의 행 잠금,
 * 요청 해시 비교 및 처리 완료 상태 변경을 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(
    propagation = Propagation.MANDATORY
)
public class ReservationIdempotencyService {

    private static final int
        MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final ReservationIdempotencyRepository
        reservationIdempotencyRepository;

    private final ReservationRequestHasher
        reservationRequestHasher;

    /**
     * 회원과 멱등성 키를 기준으로 처리 정보를
     * 생성하거나 기존 처리 정보를 조회합니다.
     *
     * 반드시 예약 생성 전체 트랜잭션 내부에서 호출해야 합니다.
     * 멱등성 처리 정보 생성 또는 잠금 조회 중 락 대기 시간이
     * 초과되면 예약 전용 락 타임아웃 예외로 변환합니다.
     */
    public ReservationIdempotency getOrCreate(
        Long memberId,
        String idempotencyKey,
        ReservationCreateRequestDto request
    ) {
        validateMemberId(memberId);
        validateIdempotencyKey(idempotencyKey);

        String requestHash =
            reservationRequestHasher.hash(request);

        try {
            /*
             * 행이 없으면 PROCESSING 상태로 생성합니다.
             *
             * 같은 회원과 키를 사용하는 동시 요청은
             * DB Unique Constraint에 의해 선행 트랜잭션이
             * 완료될 때까지 여기에서 대기합니다.
             */
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    idempotencyKey,
                    requestHash
                );

            ReservationIdempotency idempotency =
                reservationIdempotencyRepository
                    .findByMemberIdAndIdempotencyKeyForUpdate(
                        memberId,
                        idempotencyKey
                    )
                    .orElseThrow(() ->
                        new IllegalStateException(
                            "예약 멱등성 처리 정보를 "
                                + "조회할 수 없습니다."
                        )
                    );

            /*
             * 같은 키를 다른 객실, 숙박 기간 또는
             * 예약 인원에 재사용하면 충돌로 처리합니다.
             */
            if (
                !idempotency.matchesRequestHash(
                    requestHash
                )
            ) {
                throw new BusinessException(
                    ErrorCode
                        .RESERVATION_IDEMPOTENCY_CONFLICT
                );
            }

            return idempotency;
        } catch (
            PessimisticLockingFailureException exception
        ) {
            throw new BusinessException(
                ErrorCode.RESERVATION_LOCK_TIMEOUT
            );
        }
    }

    /**
     * 최초 요청으로 생성된 예약을 멱등성 처리 정보에
     * 연결하고 완료 상태로 변경합니다.
     *
     * Entity 변경 감지를 통해 트랜잭션 커밋 시 반영됩니다.
     */
    public void complete(
        ReservationIdempotency idempotency,
        Reservation reservation
    ) {
        if (idempotency == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }

        idempotency.complete(reservation);
    }

    private void validateMemberId(
        Long memberId
    ) {
        if (
            memberId == null
                || memberId <= 0
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    private void validateIdempotencyKey(
        String idempotencyKey
    ) {
        if (
            idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length()
                > MAX_IDEMPOTENCY_KEY_LENGTH
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}
