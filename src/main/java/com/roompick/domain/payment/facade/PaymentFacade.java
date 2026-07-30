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
 * 결제와 예약 Service를 조합해
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
     * 클라이언트가 PortOne 결제창을 열 수 있도록
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
     * 기존 Mock 결제를 승인하고 예약을 확정합니다.
     */
    @Transactional
    public PaymentApproveResponseDto approvePayment(
        Long paymentId,
        Long memberId,
        long requestedAmount
    ) {
        Payment payment =
            paymentService.findById(
                paymentId
            );

        Reservation reservation =
            payment.getReservation();

        LocalDateTime approvedAt =
            LocalDateTime
                .now(SERVICE_ZONE_ID)
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        Payment approvedPayment =
            paymentService.approvePayment(
                payment,
                requestedAmount,
                approvedAt
            );

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
     */
    @Transactional
    public PaymentFailResponseDto failPayment(
        Long paymentId,
        Long memberId
    ) {
        Payment payment =
            paymentService.findById(
                paymentId
            );

        Reservation reservation =
            payment.getReservation();

        LocalDateTime failedAt =
            LocalDateTime
                .now(SERVICE_ZONE_ID)
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        Payment failedPayment =
            paymentService.failPayment(
                payment,
                failedAt
            );

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
     * 외부 API 호출 중에는 DB 트랜잭션을 유지하지 않습니다.
     */
    public PaymentCompleteResponseDto
    completePortOnePayment(
        Long paymentId,
        Long memberId
    ) {

        /*
         * PortOne 외부 API를 호출하기 전에
         * 요청 회원의 결제인지, READY 상태인지 검증합니다.
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

        PortOnePaymentVerificationResultDto
            verificationResult =
            portOnePaymentVerifier.verify(
                portOnePayment,
                expectedPortOnePaymentId,
                expectedAmount
            );

        /*
         * 외부 응답 검증이 완료된 뒤
         * 짧은 쓰기 트랜잭션을 시작합니다.
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
     * DB 상태 변경 트랜잭션입니다.
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
         * 외부 API 응답을 기다리는 동안
         * 소유자나 결제 상태가 변경됐을 수 있으므로
         * 쓰기 트랜잭션 안에서 다시 검증합니다.
         */
        Payment payment =
            paymentService
                .findForPortOneCompletion(
                    paymentId,
                    memberId
                );

        validatePortOnePaymentIdUnchanged(
            payment,
            verifiedPortOnePaymentId
        );

        Reservation reservation =
            payment.getReservation();

        Payment approvedPayment =
            paymentService
                .approvePortOnePayment(
                    payment,
                    verificationResult
                        .transactionId(),
                    verificationResult.amount(),
                    verificationResult.paidAt()
                );

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
