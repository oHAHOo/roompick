package com.roompick.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 결제 생성, 조회와 결제 상태 관리를 담당하는 Service입니다.
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
     *
     * 일반적인 결제 조회에 사용하며
     * 비관적 락은 적용하지 않습니다.
     */
    @Transactional(readOnly = true)
    public Payment findById(
        Long paymentId
    ) {
        validatePaymentId(paymentId);

        return paymentRepository
            .findById(paymentId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND
                )
            );
    }

    /**
     * 예약 ID를 기준으로 결제를 조회합니다.
     */
    @Transactional(readOnly = true)
    public Payment findByReservationId(
        Long reservationId
    ) {
        if (reservationId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }

        return paymentRepository
            .findByReservationId(
                reservationId
            )
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
     * 외부 API 호출 중에는 DB 락을 유지하지 않기 위해
     * 일반 조회 메서드를 사용합니다.
     *
     * 이 단계에서는 READY 상태만 결제 완료 처리를
     * 진행할 수 있도록 검증합니다.
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

        validatePortOneCompletion(
            payment,
            memberId
        );

        return payment;
    }

    /**
     * 결제 상태를 변경하기 직전에 Payment를
     * 비관적 쓰기 락과 함께 조회합니다.
     *
     * Mock 승인, Mock 실패, PortOne 결제 완료와 같이
     * Payment 상태를 변경하는 모든 흐름에서
     * 공통으로 사용합니다.
     *
     * 같은 Payment의 상태를 변경하려는 다른 트랜잭션은
     * 현재 트랜잭션이 종료될 때까지 대기합니다.
     *
     * 이 메서드에서는 결제 소유권만 검증하고
     * READY 상태는 검증하지 않습니다.
     *
     * 락을 획득한 뒤 READY, PAID, FAILED, REFUNDED 등
     * 최신 상태를 조회할 수 있어야 하기 때문입니다.
     *
     * 실제 상태 전이 가능 여부는 Payment Entity의
     * approve(), approveWithPortOne(), fail() 메서드에서
     * 검증합니다.
     *
     * 비관적 쓰기 락을 사용하므로
     * readOnly 트랜잭션으로 설정하지 않습니다.
     */
    @Transactional
    public Payment findForPaymentTransitionForUpdate(
        Long paymentId,
        Long memberId
    ) {
        validatePaymentId(paymentId);
        validateMemberId(memberId);

        try {
            Payment payment =
                paymentRepository
                    .findByIdForUpdate(paymentId)
                    .orElseThrow(() ->
                        new BusinessException(
                            ErrorCode.PAYMENT_NOT_FOUND
                        )
                    );

            validatePaymentOwner(
                payment,
                memberId
            );

            return payment;
        } catch (
            PessimisticLockingFailureException exception
        ) {
            throw new BusinessException(
                ErrorCode.PAYMENT_LOCK_TIMEOUT
            );
        }
    }

    /**
     * 기존 Mock 결제를 승인합니다.
     *
     * Payment Entity에서 READY 상태와
     * 요청 금액 일치 여부를 검증합니다.
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
     *
     * Payment Entity에서 READY 상태와
     * 검증된 결제 금액을 다시 확인합니다.
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
     *
     * Payment Entity에서 READY 상태인지 검증한 뒤
     * FAILED 상태로 변경합니다.
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

    /**
     * PortOne 외부 API 호출 전에
     * 결제 완료 처리가 가능한지 검증합니다.
     *
     * 외부 API를 불필요하게 호출하지 않도록
     * 소유권과 READY 상태를 함께 확인합니다.
     */
    private void validatePortOneCompletion(
        Payment payment,
        Long memberId
    ) {
        validatePaymentOwner(
            payment,
            memberId
        );

        validateReadyStatus(payment);
    }

    /**
     * 결제에 연결된 예약이
     * 요청 회원의 예약인지 검증합니다.
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

        if (reservation.getMember() == null) {
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
     * 결제가 상태 변경 가능한
     * READY 상태인지 검증합니다.
     *
     * PortOne 외부 API 호출 전 사전 검증에서만
     * 사용합니다.
     *
     * 공통 비관적 락 조회 메서드에서는
     * 이 검증을 호출하지 않습니다.
     */
    private void validateReadyStatus(
        Payment payment
    ) {
        validatePayment(payment);

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
     * Payment 객체가 정상적으로 전달됐는지 검증합니다.
     */
    private void validatePayment(
        Payment payment
    ) {
        if (payment == null) {
            throw new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND
            );
        }
    }

    /**
     * Reservation 객체와 식별값이 정상인지 검증합니다.
     */
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
     * 인증된 회원 ID가 정상적으로 전달됐는지 검증합니다.
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

    /**
     * 동일한 예약에 결제가 이미 생성됐는지 확인합니다.
     */
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
}
