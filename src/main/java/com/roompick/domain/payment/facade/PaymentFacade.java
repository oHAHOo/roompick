package com.roompick.domain.payment.facade;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.reservation.entity.ReservationStatus;
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
import com.roompick.domain.waitlist.facade.WaitlistProcessingFacade;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.portone.PortOneProperties;

import lombok.RequiredArgsConstructor;

/**
 * 결제와 예약 Service를 조합하여
 * 결제 전체 흐름을 처리하는 Facade입니다.
 *
 * 결제 성공·실패 시점에 WaitlistService를 함께 호출해
 * 특가 대기열(waitlist) 상태를 예약 상태와 동기화합니다.
 * 특가 대기열을 거치지 않은 일반 예약의 결제는 연결된
 * waitlist가 없으므로 이 호출이 아무 영향을 주지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final WaitlistProcessingFacade waitlistProcessingFacade;

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

    @Transactional
    public PaymentApproveResponseDto approvePayment(
        Long paymentId,
        Long memberId,
        long requestedAmount
    ) {
        Payment payment =
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                );

        Reservation reservation =
            payment.getReservation();

        PaymentStatus currentStatus =
            payment.getStatus();

        if (
            currentStatus
                == PaymentStatus.PAID
        ) {
            return resolveExistingMockApproval(
                payment,
                reservation,
                requestedAmount
            );
        }

        if (
            currentStatus
                == PaymentStatus.FAILED
        ) {
            throw new BusinessException(
                ErrorCode.PAYMENT_CONFLICT
            );
        }

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

        waitlistProcessingFacade.confirmByReservationId(
            reservation.getId()
        );

        return PaymentApproveResponseDto.from(
            approvedPayment
        );
    }

    /**
     * READY 상태의 Mock 결제를 실패 처리하고
     * 연결된 예약을 취소합니다.
     *
     * 이미 동일한 실패 처리가 완료된 경우에는
     * 상태 전이를 다시 수행하지 않고 기존 결과를 반환합니다.
     */
    @Transactional
    public PaymentFailResponseDto failPayment(
        Long paymentId,
        Long memberId
    ) {
        Payment payment =
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                );

        Reservation reservation =
            payment.getReservation();

        PaymentStatus currentStatus =
            payment.getStatus();

        if (
            currentStatus
                == PaymentStatus.FAILED
        ) {
            return resolveExistingMockFailure(
                payment,
                reservation
            );
        }

        if (
            currentStatus
                == PaymentStatus.PAID
        ) {
            throw new BusinessException(
                ErrorCode.PAYMENT_CONFLICT
            );
        }

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

        reservationService
            .cancelByPaymentFailure(
                reservation,
                memberId,
                failedAt
            );

        waitlistProcessingFacade.expireByReservationIdAndPromoteNext(
            reservation.getId(),
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
         * 현재 결제 상태에 따라 멱등성 또는 진행 가능 여부를 판단합니다.
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

        Reservation reservationSnapshot =
            paymentSnapshot.getReservation();

        PaymentStatus snapshotStatus =
            paymentSnapshot.getStatus();

        if (
            snapshotStatus
                == PaymentStatus.PAID
        ) {
            return resolveExistingPortOneCompletion(
                paymentSnapshot,
                reservationSnapshot
            );
        }

        if (
            snapshotStatus
                == PaymentStatus.FAILED
        ) {
            throw new BusinessException(
                ErrorCode.PAYMENT_CONFLICT
            );
        }

        if (
            snapshotStatus
                != PaymentStatus.READY
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            );
        }

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

    private PaymentCompleteResponseDto
    completePortOnePaymentInTransaction(
        Long paymentId,
        Long memberId,
        PortOnePaymentVerificationResultDto
            verificationResult
    ) {
        Payment payment =
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                );

        Reservation reservation =
            payment.getReservation();

        PaymentStatus currentStatus =
            payment.getStatus();

        if (
            currentStatus
                == PaymentStatus.PAID
        ) {
            return resolveExistingPortOneCompletionAfterVerification(
                payment,
                reservation,
                verificationResult
            );
        }

        if (
            currentStatus
                == PaymentStatus.FAILED
        ) {
            throw new BusinessException(
                ErrorCode.PAYMENT_CONFLICT
            );
        }

        if (
            currentStatus
                != PaymentStatus.READY
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            );
        }

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

        waitlistProcessingFacade.confirmByReservationId(
            reservation.getId()
        );

        return PaymentCompleteResponseDto.from(
            approvedPayment
        );
    }
    /**
     * 이미 승인된 Mock 결제 재요청의 멱등성을 처리합니다.
     *
     * Payment와 Reservation이 모두 정상 완료 상태이고,
     * 기존 결제 금액과 재요청 금액이 같은 경우에만
     * 기존 성공 결과를 반환합니다.
     */
    private PaymentApproveResponseDto
    resolveExistingMockApproval(
        Payment payment,
        Reservation reservation,
        long requestedAmount
    ) {
        validateApprovedStateConsistency(
            reservation
        );

        validateIdempotentApprovalAmount(
            payment,
            requestedAmount
        );

        return PaymentApproveResponseDto.from(
            payment
        );
    }

    /**
     * 승인 완료된 Payment와 Reservation의
     * 상태가 일치하는지 검증합니다.
     */
    private void validateApprovedStateConsistency(
        Reservation reservation
    ) {
        if (
            reservation == null
                || reservation.getStatus()
                != ReservationStatus.CONFIRMED
        ) {
            throw new BusinessException(
                ErrorCode
                    .PAYMENT_STATE_INCONSISTENCY
            );
        }
    }

    /**
     * 최초 승인 금액과 재요청 금액이
     * 동일한지 검증합니다.
     */
    private void validateIdempotentApprovalAmount(
        Payment payment,
        long requestedAmount
    ) {
        if (
            payment.getAmount()
                != requestedAmount
        ) {
            throw new BusinessException(
                ErrorCode
                    .PAYMENT_IDEMPOTENCY_CONFLICT
            );
        }
    }

    /**
     * 이미 실패 처리된 Mock 결제 재요청의
     * 멱등성을 처리합니다.
     */
    private PaymentFailResponseDto
    resolveExistingMockFailure(
        Payment payment,
        Reservation reservation
    ) {
        validateFailedStateConsistency(
            reservation
        );

        return PaymentFailResponseDto.from(
            payment
        );
    }

    /**
     * 실패 처리된 Payment에 연결된 Reservation이
     * 정상적으로 취소됐는지 확인합니다.
     */
    private void validateFailedStateConsistency(
        Reservation reservation
    ) {
        if (
            reservation == null
                || reservation.getStatus()
                != ReservationStatus.CANCELED
        ) {
            throw new BusinessException(
                ErrorCode
                    .PAYMENT_STATE_INCONSISTENCY
            );
        }
    }

    /**
     * 이미 완료된 PortOne 결제 재요청에 대해
     * 기존 성공 결과를 반환합니다.
     */
    private PaymentCompleteResponseDto
    resolveExistingPortOneCompletion(
        Payment payment,
        Reservation reservation
    ) {
        validateApprovedStateConsistency(
            reservation
        );

        validateCompletedPortOnePayment(
            payment
        );

        return PaymentCompleteResponseDto.from(
            payment
        );
    }

    /**
     * 완료된 PortOne 결제에 필요한 정보가
     * 정상적으로 저장되어 있는지 확인합니다.
     */
    private void validateCompletedPortOnePayment(
        Payment payment
    ) {
        if (
            payment.getPortOnePaymentId() == null
                || payment
                .getPortOneTransactionId()
                == null
                || payment.getApprovedAt() == null
        ) {
            throw new BusinessException(
                ErrorCode
                    .PAYMENT_STATE_INCONSISTENCY
            );
        }
    }

    /**
     * 외부 PortOne 검증까지 마친 요청이 락을 획득했을 때,
     * 다른 요청이 먼저 동일 결제를 완료했다면
     * 기존 성공 결과를 반환합니다.
     */
    private PaymentCompleteResponseDto
    resolveExistingPortOneCompletionAfterVerification(
        Payment payment,
        Reservation reservation,
        PortOnePaymentVerificationResultDto
            verificationResult
    ) {
        validateApprovedStateConsistency(
            reservation
        );

        validateCompletedPortOnePayment(
            payment
        );

        validateExistingPortOneResultMatches(
            payment,
            verificationResult
        );

        return PaymentCompleteResponseDto.from(
            payment
        );
    }

    /**
     * 먼저 완료된 PortOne 결제 정보와
     * 현재 요청이 검증한 결제 정보가 같은지 확인합니다.
     */
    private void validateExistingPortOneResultMatches(
        Payment payment,
        PortOnePaymentVerificationResultDto
            verificationResult
    ) {
        boolean transactionIdMatches =
            Objects.equals(
                payment.getPortOneTransactionId(),
                verificationResult.transactionId()
            );

        boolean amountMatches =
            payment.getAmount()
                == verificationResult.amount();

        boolean paidAtMatches =
            Objects.equals(
                payment.getApprovedAt(),
                verificationResult.paidAt()
            );

        if (
            !transactionIdMatches
                || !amountMatches
                || !paidAtMatches
        ) {
            throw new BusinessException(
                ErrorCode
                    .PAYMENT_IDEMPOTENCY_CONFLICT
            );
        }
    }
}
