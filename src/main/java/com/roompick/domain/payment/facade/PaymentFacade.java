package com.roompick.domain.payment.facade;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.roompick.domain.payment.client.portone.PortOneClient;
import com.roompick.domain.payment.client.portone.PortOnePaymentVerifier;
import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentResponseDto;
import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentVerificationResultDto;
import com.roompick.domain.payment.dto.response.PaymentApproveResponseDto;
import com.roompick.domain.payment.dto.response.PaymentCompleteResponseDto;
import com.roompick.domain.payment.dto.response.PaymentFailResponseDto;
import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.service.PaymentService;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.portone.PortOneProperties;

import lombok.RequiredArgsConstructor;

/**
 * 결제와 예약 Service를 조합하여
 * 결제 전체 흐름을 처리하는 Facade입니다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private final ReservationService reservationService;
    private final PaymentService paymentService;

    private final PortOneClient portOneClient;
    private final PortOnePaymentVerifier portOnePaymentVerifier;
    private final PortOneProperties portOneProperties;

    private final TransactionTemplate transactionTemplate;

    /**
     * 예약을 검증하고 READY 상태의 결제를 준비합니다.
     *
     * 클라이언트가 PortOne 결제창을 호출할 수 있도록
     * storeId와 channelKey도 함께 반환합니다.
     */
    @Transactional
    public PaymentPrepareResponseDto preparePayment(
        Long reservationId,
        Long memberId
    ) {
        Reservation reservation =
            reservationService
                .findForPaymentPreparation(
                    reservationId,
                    memberId
                );

        Payment payment =
            paymentService.preparePayment(
                reservation
            );

        return PaymentPrepareResponseDto.from(
            payment,
            portOneProperties.storeId(),
            portOneProperties.channelKey()
        );
    }

    /**
     * 기존 Mock 결제를 승인하고
     * 연결된 예약을 확정합니다.
     *
     * Payment를 비관적 쓰기 락으로 조회하여
     * 동일 결제에 대한 승인 및 실패 요청을
     * 순차적으로 처리합니다.
     */
    @Transactional
    public PaymentApproveResponseDto approvePayment(
        Long paymentId,
        Long memberId,
        long requestedAmount
    ) {
        /*
         * Payment 행을 PESSIMISTIC_WRITE 락으로 조회합니다.
         *
         * 동일 Payment에 대한 다른 승인 또는 실패 요청은
         * 현재 트랜잭션이 종료될 때까지 대기합니다.
         *
         * 공통 락 조회에서는 결제 소유권만 확인하고,
         * READY 상태와 금액은 실제 승인 메서드에서
         * 검증합니다.
         */
        Payment payment =
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                );

        Reservation reservation =
            payment.getReservation();

        LocalDateTime approvedAt =
            LocalDateTime
                .now(SERVICE_ZONE_ID)
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        /*
         * Payment Entity에서 다음 내용을 검증합니다.
         *
         * 1. 현재 상태가 READY인지
         * 2. 요청 금액이 Payment 금액과 일치하는지
         *
         * 검증을 통과하면 READY에서 PAID로 변경합니다.
         */
        Payment approvedPayment =
            paymentService.approvePayment(
                payment,
                requestedAmount,
                approvedAt
            );

        /*
         * Payment 승인과 Reservation 확정을
         * 같은 트랜잭션에서 처리합니다.
         *
         * 예약 확정 과정에서 예외가 발생하면
         * Payment 변경도 함께 롤백됩니다.
         */
        reservationService.confirmPayment(
            reservation,
            memberId,
            approvedAt
        );

        return PaymentApproveResponseDto.from(
            approvedPayment
        );
    }

    /**
     * READY 상태의 기존 Mock 결제를 실패 처리하고
     * 연결된 예약을 취소합니다.
     *
     * Payment를 비관적 쓰기 락으로 조회하여
     * 동일 결제에 대한 승인 및 실패 요청을
     * 순차적으로 처리합니다.
     */
    @Transactional
    public PaymentFailResponseDto failPayment(
        Long paymentId,
        Long memberId
    ) {
        /*
         * Mock 승인과 동일한 공통 락 조회를 사용합니다.
         *
         * 승인 요청과 실패 요청이 동시에 들어오더라도
         * 먼저 락을 획득한 하나의 요청만 READY 상태를
         * 변경할 수 있습니다.
         */
        Payment payment =
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                );

        Reservation reservation =
            payment.getReservation();

        LocalDateTime failedAt =
            LocalDateTime
                .now(SERVICE_ZONE_ID)
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        /*
         * Payment Entity에서 현재 상태가 READY인지
         * 검증한 뒤 FAILED 상태로 변경합니다.
         */
        Payment failedPayment =
            paymentService.failPayment(
                payment,
                failedAt
            );

        /*
         * Payment 실패 처리와 Reservation 취소를
         * 같은 트랜잭션에서 처리합니다.
         *
         * 예약 취소 과정에서 예외가 발생하면
         * Payment 변경도 함께 롤백됩니다.
         */
        reservationService.cancelByPaymentFailure(
            reservation,
            memberId,
            failedAt
        );

        return PaymentFailResponseDto.from(
            failedPayment
        );
    }

    /**
     * PortOne의 실제 결제 정보를 조회하고 검증한 뒤
     * Payment와 Reservation을 확정합니다.
     *
     * 외부 PortOne API를 호출하는 동안에는
     * DB 트랜잭션과 비관적 락을 유지하지 않습니다.
     */
    public PaymentCompleteResponseDto
    completePortOnePayment(
        Long paymentId,
        Long memberId
    ) {

        /*
         * PortOne 외부 API 호출 전 일반 조회입니다.
         *
         * 요청 회원의 결제인지 확인하고,
         * Payment가 READY 상태인지 검증합니다.
         *
         * 외부 API를 호출하는 동안 DB 락을
         * 유지하지 않기 위해 일반 조회를 사용합니다.
         */
        Payment paymentSnapshot =
            paymentService
                .findForPortOneCompletion(
                    paymentId,
                    memberId
                );

        String expectedPortOnePaymentId =
            paymentSnapshot
                .getPortOnePaymentId();

        long expectedAmount =
            paymentSnapshot.getAmount();

        /*
         * 외부 PortOne API 호출은
         * DB 트랜잭션 밖에서 수행합니다.
         */
        PortOnePaymentResponseDto portOnePayment =
            portOneClient.getPayment(
                expectedPortOnePaymentId
            );

        /*
         * PortOne 응답의 결제 ID, 상태, 금액,
         * 거래 식별값과 완료 시각을 검증합니다.
         */
        PortOnePaymentVerificationResultDto
            verificationResult =
            portOnePaymentVerifier.verify(
                portOnePayment,
                expectedPortOnePaymentId,
                expectedAmount
            );

        /*
         * 외부 API 응답 검증이 완료된 뒤에만
         * 짧은 DB 쓰기 트랜잭션을 시작합니다.
         */
        PaymentCompleteResponseDto result =
            transactionTemplate.execute(
                transactionStatus ->
                    completePortOnePaymentInTransaction(
                        paymentId,
                        memberId,
                        expectedPortOnePaymentId,
                        verificationResult
                    )
            );

        if (result == null) {
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        return result;
    }

    /**
     * PortOne 응답 검증 이후 실행되는
     * 결제 및 예약 상태 변경 트랜잭션입니다.
     *
     * Payment를 공통 비관적 쓰기 락 메서드로 조회해
     * 동일 결제의 모든 상태 변경 요청과 경합을 방지합니다.
     */
    private PaymentCompleteResponseDto
    completePortOnePaymentInTransaction(
        Long paymentId,
        Long memberId,
        String verifiedPortOnePaymentId,
        PortOnePaymentVerificationResultDto
            verificationResult
    ) {

        /*
         * Payment 행을 PESSIMISTIC_WRITE 락으로 조회합니다.
         *
         * Mock 승인, Mock 실패, PortOne 결제 완료가
         * 모두 같은 공통 락 조회 메서드를 사용합니다.
         *
         * 공통 락 조회에서는 결제 소유권만 검증합니다.
         * 실제 READY 상태 검증은
         * approvePortOnePayment()에서 수행합니다.
         */
        Payment payment =
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                );

        /*
         * 외부 API를 호출하는 동안 내부 결제 식별값이
         * 변경되지 않았는지 다시 확인합니다.
         */
        validatePortOnePaymentIdUnchanged(
            payment,
            verifiedPortOnePaymentId
        );

        Reservation reservation =
            payment.getReservation();

        /*
         * Payment Entity에서 최신 상태가 READY인지,
         * 검증 금액이 내부 결제 금액과 같은지 확인합니다.
         *
         * 다른 요청이 먼저 처리해 현재 상태가 PAID 또는
         * FAILED라면 INVALID_PAYMENT_STATUS가 발생합니다.
         */
        Payment approvedPayment =
            paymentService
                .approvePortOnePayment(
                    payment,
                    verificationResult
                        .transactionId(),
                    verificationResult.amount(),
                    verificationResult.paidAt()
                );

        /*
         * Payment 승인과 Reservation 확정을
         * 같은 짧은 DB 트랜잭션에서 처리합니다.
         */
        reservationService.confirmPayment(
            reservation,
            memberId,
            verificationResult.paidAt()
        );

        return PaymentCompleteResponseDto.from(
            approvedPayment
        );
    }

    /**
     * 외부 API 호출 전후 내부 PortOne 결제 ID가
     * 변경되지 않았는지 확인합니다.
     */
    private void validatePortOnePaymentIdUnchanged(
        Payment payment,
        String verifiedPortOnePaymentId
    ) {
        String currentPortOnePaymentId =
            payment.getPortOnePaymentId();

        if (
            currentPortOnePaymentId == null
                || !currentPortOnePaymentId.equals(
                verifiedPortOnePaymentId
            )
        ) {
            throw new BusinessException(
                ErrorCode
                    .PORTONE_PAYMENT_ID_MISMATCH
            );
        }
    }
}
