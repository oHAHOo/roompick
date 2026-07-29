package com.roompick.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Objects;

import com.roompick.domain.payment.entity.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 결제 생성과 결제 상태 관리를 담당하는 Service입니다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 예약에 대한 결제를 준비합니다.
     *
     * 동일한 예약에 결제가 이미 존재하면
     * 새 결제를 생성하지 않습니다.
     */
    @Transactional
    public Payment preparePayment(
        Reservation reservation
    ) {
        validateReservation(reservation);

        validatePaymentNotDuplicated(
            reservation.getId()
        );

        Payment payment =
            Payment.create(reservation);

        return paymentRepository.save(payment);
    }

    /**
     * 결제 ID를 기준으로 결제를 조회합니다.
     */
    @Transactional(readOnly = true)
    public Payment findById(Long paymentId) {
        return paymentRepository
            .findById(paymentId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND
                )
            );
    }

    /**
     * PortOne 외부 API를 호출하기 전에
     * 결제 소유권과 현재 상태를 검증합니다.
     *
     * PortOne 호출 이후 쓰기 트랜잭션 안에서도
     * 동일한 메서드를 호출해 상태를 다시 검증합니다.
     */
    @Transactional(readOnly = true)
    public Payment findForPortOneCompletion(
        Long paymentId,
        Long memberId
    ) {
        validatePaymentId(paymentId);
        validateMemberId(memberId);

        Payment payment =
            paymentRepository
                .findByIdWithReservationAndMember(
                    paymentId
                )
                .orElseThrow(() ->
                    new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND
                    )
                );

        validatePaymentOwner(
            payment,
            memberId
        );

        validateReadyStatus(payment);

        return payment;
    }

    /**
     * 예약 ID를 기준으로 결제를 조회합니다.
     */
    @Transactional(readOnly = true)
    public Payment findByReservationId(
        Long reservationId
    ) {
        return paymentRepository
            .findByReservationId(reservationId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND
                )
            );
    }

    /**
     * 기존 Mock 결제를 승인합니다.
     */
    @Transactional
    public Payment approvePayment(
        Payment payment,
        long requestedAmount,
        LocalDateTime approvedAt
    ) {
        validatePayment(payment);

        payment.approve(
            requestedAmount,
            approvedAt
        );

        return payment;
    }

    /**
     * PortOne에서 검증된 결제 정보를 기준으로
     * 결제를 승인합니다.
     */
    @Transactional
    public Payment approvePortOnePayment(
        Payment payment,
        String portOneTransactionId,
        long verifiedAmount,
        LocalDateTime approvedAt
    ) {
        validatePayment(payment);

        payment.approveWithPortOne(
            portOneTransactionId,
            verifiedAmount,
            approvedAt
        );

        return payment;
    }

    /**
     * READY 상태의 결제를 실패 처리합니다.
     */
    @Transactional
    public Payment failPayment(
        Payment payment,
        LocalDateTime failedAt
    ) {
        validatePayment(payment);

        payment.fail(failedAt);

        return payment;
    }

    private void validatePayment(
        Payment payment
    ) {
        if (payment == null) {
            throw new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND
            );
        }
    }

    private void validateReservation(
        Reservation reservation
    ) {
        if (reservation == null) {
            throw new BusinessException(
                ErrorCode.RESERVATION_NOT_FOUND
            );
        }

        if (reservation.getId() == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    private void validatePaymentNotDuplicated(
        Long reservationId
    ) {
        boolean paymentExists =
            paymentRepository
                .existsByReservationId(
                    reservationId
                );

        if (paymentExists) {
            throw new BusinessException(
                ErrorCode.PAYMENT_ALREADY_EXISTS
            );
        }
    }

    /**
     * 결제에 연결된 예약이 요청 회원의 예약인지 검증합니다.
     */
    private void validatePaymentOwner(
        Payment payment,
        Long memberId
    ) {
        validatePayment(payment);

        Reservation reservation =
            payment.getReservation();

        if (reservation == null) {
            throw new BusinessException(
                ErrorCode.RESERVATION_NOT_FOUND
            );
        }

        Long reservationMemberId =
            reservation
                .getMember()
                .getId();

        if (!Objects.equals(
            reservationMemberId,
            memberId
        )) {
            throw new BusinessException(
                ErrorCode.RESERVATION_ACCESS_DENIED
            );
        }
    }

    /**
     * PortOne 결제 완료 처리가 가능한
     * READY 상태인지 검증합니다.
     */
    private void validateReadyStatus(
        Payment payment
    ) {
        if (
            payment.getStatus()
                != PaymentStatus.READY
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            );
        }
    }

    /**
     * 결제 ID가 정상적으로 전달됐는지 검증합니다.
     */
    private void validatePaymentId(
        Long paymentId
    ) {
        if (paymentId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    /**
     * 인증된 회원 ID가 전달됐는지 검증합니다.
     */
    private void validateMemberId(
        Long memberId
    ) {
        if (memberId == null) {
            throw new BusinessException(
                ErrorCode.UNAUTHORIZED
            );
        }
    }
}
