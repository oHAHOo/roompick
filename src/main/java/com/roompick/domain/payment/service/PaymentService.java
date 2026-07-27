package com.roompick.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

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
     * 동일한 예약에 결제가 이미 존재하면 새 결제를 생성하지 않습니다.
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
        return paymentRepository.findById(paymentId)
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
        return paymentRepository
            .findByReservationId(reservationId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND
                )
            );
    }

    /**
     * READY 상태의 결제를 승인합니다.
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

    private void validatePayment(Payment payment) {
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
            paymentRepository.existsByReservationId(
                reservationId
            );

        if (paymentExists) {
            throw new BusinessException(
                ErrorCode.PAYMENT_ALREADY_EXISTS
            );
        }
    }
}
