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

import lombok.RequiredArgsConstructor;

/**
 * 예약과 결제 도메인의 결제 흐름을 조율합니다.
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

    private final TransactionTemplate transactionTemplate;

    /**
     * 회원의 예약을 확인하고 READY 상태의 결제를 생성합니다.
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
            payment
        );
    }

    /**
     * Mock 결제를 승인하고 예약을 확정합니다.
     *
     * 결제 상태와 금액을 먼저 검증하여
     * 이미 처리된 결제에는 INVALID_PAYMENT_STATUS를 반환합니다.
     *
     * 이후 예약 소유자, 예약 상태, 결제 만료 여부를 검증하고
     * 예약을 확정합니다.
     *
     * 두 상태 변경은 같은 트랜잭션에서 처리되므로
     * 예약 검증에 실패하면 먼저 변경된 결제 상태도 롤백됩니다.
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
            LocalDateTime.now(
                    SERVICE_ZONE_ID
                )
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        /*
         * 결제 상태와 요청 금액을 먼저 검증합니다.
         *
         * 이미 PAID 상태인 결제라면
         * 예약 상태 검증보다 먼저
         * INVALID_PAYMENT_STATUS가 발생합니다.
         */
        Payment approvedPayment =
            paymentService.approvePayment(
                payment,
                requestedAmount,
                approvedAt
            );

        /*
         * 결제와 연결된 예약의 소유자, 상태,
         * 만료 여부를 검증한 뒤 예약을 확정합니다.
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
     * READY 상태의 Mock 결제를 실패 처리하고
     * 연결된 예약을 취소합니다.
     *
     * 결제 상태를 먼저 검증하여 이미 처리된 결제에는
     * INVALID_PAYMENT_STATUS를 반환합니다.
     *
     * 결제와 예약 상태 변경은 하나의 트랜잭션에서 처리되므로
     * 예약 검증에 실패하면 Payment 변경도 롤백됩니다.
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
            LocalDateTime.now(
                    SERVICE_ZONE_ID
                )
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        /*
         * Payment가 READY 상태인지 먼저 검증한 뒤
         * FAILED 상태로 변경합니다.
         */
        Payment failedPayment =
            paymentService.failPayment(
                payment,
                failedAt
            );

        /*
         * 연결된 예약의 소유자와 상태를 검증한 뒤
         * CANCELED 상태로 변경합니다.
         *
         * 결제 대기 시간이 만료된 예약도
         * 객실 점유를 해제해야 하므로
         * 만료 여부는 검사하지 않습니다.
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
     * PortOne에서 실제 결제 정보를 조회하고
     * 검증이 완료되면 결제와 예약을 확정합니다.
     *
     * 외부 API 호출에는 DB 트랜잭션을 적용하지 않고,
     * 검증 이후 상태 변경 구간에만
     * 짧은 트랜잭션을 적용합니다.
     */
    public PaymentCompleteResponseDto completePortOnePayment(
        Long paymentId,
        Long memberId
    ) {
        /*
         * PortOne 외부 API 호출 전에
         * 요청 회원의 소유권과 Payment READY 상태를 검증합니다.
         */
        Payment paymentSnapshot =
            paymentService
                .findForPortOneCompletion(
                    paymentId,
                    memberId
                );

        String expectedPortOnePaymentId =
            paymentSnapshot.getPortOnePaymentId();

        long expectedAmount =
            paymentSnapshot.getAmount();

        /*
         * PortOne 외부 API 호출은
         * DB 트랜잭션 밖에서 실행합니다.
         *
         * PortOne 응답이 늦어져도 DB 트랜잭션과
         * 커넥션을 장시간 점유하지 않습니다.
         */
        PortOnePaymentResponseDto portOnePayment =
            portOneClient.getPayment(
                expectedPortOnePaymentId
            );

        /*
         * PortOne 결제 ID, 상태, 금액,
         * 거래 식별값과 결제 완료 시각을 검증합니다.
         */
        PortOnePaymentVerificationResultDto
            verificationResult =
            portOnePaymentVerifier.verify(
                portOnePayment,
                expectedPortOnePaymentId,
                expectedAmount
            );

        /*
         * 외부 결제 검증이 완료된 뒤
         * 결제와 예약 상태를 변경하는
         * 짧은 DB 트랜잭션을 실행합니다.
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
     * PortOne 검증이 완료된 결제의 상태를 변경합니다.
     *
     * 이 메서드는 TransactionTemplate이 생성한
     * DB 트랜잭션 안에서 호출됩니다.
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
         * 외부 API 응답을 기다리는 사이 상태가 변경됐을 수 있으므로
         * 쓰기 트랜잭션 안에서 소유권과 READY 상태를 다시 검증합니다.
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

        /*
         * PortOne에서 검증된 거래 번호, 결제 금액,
         * 결제 완료 시각을 Payment에 반영합니다.
         */
        Payment approvedPayment =
            paymentService.approvePortOnePayment(
                payment,
                verificationResult.transactionId(),
                verificationResult.amount(),
                verificationResult.paidAt()
            );

        /*
         * 예약 소유자, 예약 상태, 결제 만료 여부를
         * 검증한 뒤 예약을 확정합니다.
         *
         * 예약 검증에 실패하면 위 Payment 변경도
         * 함께 롤백됩니다.
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
     * PortOne 조회 전후로 내부 결제 식별값이
     * 변경되지 않았는지 확인합니다.
     */
    private void validatePortOnePaymentIdUnchanged(
        Payment payment,
        String verifiedPortOnePaymentId
    ) {
        String currentPortOnePaymentId =
            payment.getPortOnePaymentId();

        if (currentPortOnePaymentId == null
            || !currentPortOnePaymentId.equals(
            verifiedPortOnePaymentId
        )) {

            throw new BusinessException(
                ErrorCode.PORTONE_PAYMENT_ID_MISMATCH
            );
        }
    }
}
