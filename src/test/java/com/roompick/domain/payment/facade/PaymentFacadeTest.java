package com.roompick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
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
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.service.PaymentService;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.portone.PortOneProperties;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    private static final Long PORTONE_PAYMENT_INTERNAL_ID =
        100L;

    private static final Long PORTONE_RESERVATION_ID =
        1L;

    private static final Long PORTONE_MEMBER_ID =
        10L;

    private static final String PORTONE_PAYMENT_ID =
        "roompick-payment-test-001";

    private static final String PORTONE_TRANSACTION_ID =
        "transaction-test-001";

    private static final String PORTONE_STORE_ID =
        "store-test-001";

    private static final String PORTONE_CHANNEL_KEY =
        "channel-key-test-001";

    private static final long PORTONE_PAYMENT_AMOUNT =
        200_000L;

    private static final LocalDateTime PORTONE_PAID_AT =
        LocalDateTime.of(
            2026,
            7,
            29,
            17,
            0
        );

    @Mock
    private ReservationService reservationService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PortOneClient portOneClient;

    @Mock
    private PortOnePaymentVerifier portOnePaymentVerifier;

    @Mock
    private PortOneProperties portOneProperties;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private Reservation reservation;

    @Mock
    private Payment payment;

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName(
        "회원의 예약을 확인하고 PortOne 설정이 포함된 READY 결제를 준비한다"
    )
    void preparePayment() {

        // given
        Long reservationId = 1L;
        Long memberId = 10L;
        Long paymentId = 100L;

        given(
            reservationService
                .findForPaymentPreparation(
                    reservationId,
                    memberId
                )
        ).willReturn(
            reservation
        );

        given(
            paymentService.preparePayment(
                reservation
            )
        ).willReturn(
            payment
        );

        given(payment.getId())
            .willReturn(paymentId);

        given(payment.getPortOnePaymentId())
            .willReturn(PORTONE_PAYMENT_ID);

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getId())
            .willReturn(reservationId);

        given(payment.getAmount())
            .willReturn(PORTONE_PAYMENT_AMOUNT);

        given(payment.getStatus())
            .willReturn(PaymentStatus.READY);

        given(portOneProperties.storeId())
            .willReturn(PORTONE_STORE_ID);

        given(portOneProperties.channelKey())
            .willReturn(PORTONE_CHANNEL_KEY);

        // when
        PaymentPrepareResponseDto response =
            paymentFacade.preparePayment(
                reservationId,
                memberId
            );

        // then
        assertThat(response.paymentId())
            .isEqualTo(paymentId);

        assertThat(response.portOnePaymentId())
            .isEqualTo(PORTONE_PAYMENT_ID);

        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.amount())
            .isEqualTo(PORTONE_PAYMENT_AMOUNT);

        assertThat(response.status())
            .isEqualTo(PaymentStatus.READY);

        assertThat(response.storeId())
            .isEqualTo(PORTONE_STORE_ID);

        assertThat(response.channelKey())
            .isEqualTo(PORTONE_CHANNEL_KEY);

        then(reservationService)
            .should()
            .findForPaymentPreparation(
                reservationId,
                memberId
            );

        then(paymentService)
            .should()
            .preparePayment(
                reservation
            );
    }

    @Test
    @DisplayName(
        "결제 상태와 금액을 먼저 검증한 뒤 예약을 확정한다"
    )
    void approvePaymentAndConfirmReservation() {

        // given
        Long paymentId = 100L;
        Long reservationId = 1L;
        Long memberId = 10L;
        long requestedAmount = 200_000L;

        LocalDateTime approvedAt =
            LocalDateTime.of(
                2026,
                7,
                27,
                15,
                30
            );

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                )
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(requestedAmount),
                any(LocalDateTime.class)
            )
        ).willReturn(payment);

        given(
            reservationService.confirmPayment(
                eq(reservation),
                eq(memberId),
                any(LocalDateTime.class)
            )
        ).willReturn(reservation);

        given(payment.getId())
            .willReturn(paymentId);

        given(reservation.getId())
            .willReturn(reservationId);

        given(payment.getAmount())
            .willReturn(requestedAmount);

        given(payment.getStatus())
            .willReturn(PaymentStatus.PAID);

        given(reservation.getStatus())
            .willReturn(
                ReservationStatus.CONFIRMED
            );

        given(payment.getApprovedAt())
            .willReturn(approvedAt);

        ArgumentCaptor<LocalDateTime>
            paymentApprovedAtCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        ArgumentCaptor<LocalDateTime>
            reservationApprovedAtCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        // when
        PaymentApproveResponseDto response =
            paymentFacade.approvePayment(
                paymentId,
                memberId,
                requestedAmount
            );

        // then
        assertThat(response.paymentId())
            .isEqualTo(paymentId);

        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.amount())
            .isEqualTo(requestedAmount);

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(response.approvedAt())
            .isEqualTo(approvedAt);

        InOrder inOrder =
            inOrder(
                paymentService,
                reservationService
            );

        inOrder.verify(paymentService)
            .approvePayment(
                eq(payment),
                eq(requestedAmount),
                paymentApprovedAtCaptor.capture()
            );

        inOrder.verify(reservationService)
            .confirmPayment(
                eq(reservation),
                eq(memberId),
                reservationApprovedAtCaptor.capture()
            );

        assertThat(
            paymentApprovedAtCaptor.getValue()
        ).isEqualTo(
            reservationApprovedAtCaptor.getValue()
        );
    }

    @Test
    @DisplayName(
        "이미 승인된 결제는 예약 검증 전에 거절한다"
    )
    void rejectDuplicatedApprovalBeforeReservationValidation() {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;
        long requestedAmount = 200_000L;

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                )
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(requestedAmount),
                any(LocalDateTime.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.approvePayment(
                    paymentId,
                    memberId,
                    requestedAmount
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        verifyNoInteractions(
            reservationService
        );
    }

    @Test
    @DisplayName(
        "결제 금액이 다르면 예약 확정 처리를 호출하지 않는다"
    )
    void rejectAmountMismatchBeforeReservationConfirmation() {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;
        long wrongAmount = 190_000L;

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                )
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.approvePayment(
                eq(payment),
                eq(wrongAmount),
                any(LocalDateTime.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.approvePayment(
                    paymentId,
                    memberId,
                    wrongAmount
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            );

        verifyNoInteractions(
            reservationService
        );
    }

    @Test
    @DisplayName(
        "결제 실패 시 Payment 실패 처리 후 Reservation을 취소한다"
    )
    void failPayment() {

        // given
        Long paymentId = 1L;
        Long memberId = 10L;

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                )
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.failPayment(
                eq(payment),
                any(LocalDateTime.class)
            )
        ).willReturn(payment);

        given(payment.getId())
            .willReturn(paymentId);

        given(payment.getAmount())
            .willReturn(200_000L);

        given(payment.getStatus())
            .willReturn(PaymentStatus.FAILED);

        given(payment.getFailedAt())
            .willReturn(
                LocalDateTime.of(
                    2026,
                    7,
                    27,
                    16,
                    0
                )
            );

        given(reservation.getId())
            .willReturn(100L);

        given(reservation.getStatus())
            .willReturn(
                ReservationStatus.CANCELED
            );

        given(reservation.getCanceledAt())
            .willReturn(
                LocalDateTime.of(
                    2026,
                    7,
                    27,
                    16,
                    0
                )
            );

        // when
        PaymentFailResponseDto result =
            paymentFacade.failPayment(
                paymentId,
                memberId
            );

        // then
        ArgumentCaptor<LocalDateTime>
            paymentTimeCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        ArgumentCaptor<LocalDateTime>
            reservationTimeCaptor =
            ArgumentCaptor.forClass(
                LocalDateTime.class
            );

        InOrder inOrder =
            inOrder(
                paymentService,
                reservationService
            );

        inOrder.verify(paymentService)
            .failPayment(
                eq(payment),
                paymentTimeCaptor.capture()
            );

        inOrder.verify(reservationService)
            .cancelByPaymentFailure(
                eq(reservation),
                eq(memberId),
                reservationTimeCaptor.capture()
            );

        assertThat(paymentTimeCaptor.getValue())
            .isEqualTo(
                reservationTimeCaptor.getValue()
            );

        assertThat(result.paymentId())
            .isEqualTo(paymentId);

        assertThat(result.reservationId())
            .isEqualTo(100L);

        assertThat(result.paymentStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(result.reservationStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );
    }

    @Test
    @DisplayName(
        "결제 상태 검증에 실패하면 예약 취소를 호출하지 않는다"
    )
    void paymentFailureValidationStopsBeforeReservationCancellation() {

        // given
        Long paymentId = 1L;
        Long memberId = 10L;

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    paymentId,
                    memberId
                )
        ).willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(
            paymentService.failPayment(
                eq(payment),
                any(LocalDateTime.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.failPayment(
                    paymentId,
                    memberId
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        then(reservationService)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "PortOne 결제 검증에 성공하면 락 조회 후 결제와 예약을 확정한다"
    )
    void completePortOnePaymentSuccessfully() {

        // given
        stubTransactionTemplate();

        Payment paymentSnapshot =
            org.mockito.Mockito.mock(
                Payment.class
            );

        Payment paymentForUpdate =
            org.mockito.Mockito.mock(
                Payment.class
            );

        Reservation portOneReservation =
            org.mockito.Mockito.mock(
                Reservation.class
            );

        PortOnePaymentResponseDto portOneResponse =
            createPaidPortOneResponse();

        PortOnePaymentVerificationResultDto
            verificationResult =
            createVerificationResult();

        /*
         * 외부 PortOne API 호출 전 일반 조회입니다.
         */
        given(
            paymentService.findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            )
        ).willReturn(
            paymentSnapshot
        );

        /*
         * 외부 응답 검증 후 트랜잭션 내부에서
         * 비관적 락과 함께 다시 조회합니다.
         */
        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    PORTONE_PAYMENT_INTERNAL_ID,
                    PORTONE_MEMBER_ID
                )
        ).willReturn(
            paymentForUpdate
        );

        given(
            paymentSnapshot.getPortOnePaymentId()
        ).willReturn(
            PORTONE_PAYMENT_ID
        );

        given(
            paymentSnapshot.getAmount()
        ).willReturn(
            PORTONE_PAYMENT_AMOUNT
        );

        given(
            portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        ).willReturn(
            portOneResponse
        );

        given(
            portOnePaymentVerifier.verify(
                portOneResponse,
                PORTONE_PAYMENT_ID,
                PORTONE_PAYMENT_AMOUNT
            )
        ).willReturn(
            verificationResult
        );

        given(
            paymentForUpdate.getPortOnePaymentId()
        ).willReturn(
            PORTONE_PAYMENT_ID
        );

        given(
            paymentForUpdate.getReservation()
        ).willReturn(
            portOneReservation
        );

        given(
            paymentService.approvePortOnePayment(
                paymentForUpdate,
                PORTONE_TRANSACTION_ID,
                PORTONE_PAYMENT_AMOUNT,
                PORTONE_PAID_AT
            )
        ).willReturn(
            paymentForUpdate
        );

        given(paymentForUpdate.getId())
            .willReturn(
                PORTONE_PAYMENT_INTERNAL_ID
            );

        given(
            paymentForUpdate.getPortOneTransactionId()
        ).willReturn(
            PORTONE_TRANSACTION_ID
        );

        given(paymentForUpdate.getAmount())
            .willReturn(
                PORTONE_PAYMENT_AMOUNT
            );

        given(paymentForUpdate.getStatus())
            .willReturn(
                PaymentStatus.PAID
            );

        given(paymentForUpdate.getApprovedAt())
            .willReturn(
                PORTONE_PAID_AT
            );

        given(portOneReservation.getId())
            .willReturn(
                PORTONE_RESERVATION_ID
            );

        given(portOneReservation.getStatus())
            .willReturn(
                ReservationStatus.CONFIRMED
            );

        // when
        PaymentCompleteResponseDto result =
            paymentFacade.completePortOnePayment(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        // then
        assertThat(result.paymentId())
            .isEqualTo(
                PORTONE_PAYMENT_INTERNAL_ID
            );

        assertThat(result.portOnePaymentId())
            .isEqualTo(
                PORTONE_PAYMENT_ID
            );

        assertThat(result.portOneTransactionId())
            .isEqualTo(
                PORTONE_TRANSACTION_ID
            );

        assertThat(result.reservationId())
            .isEqualTo(
                PORTONE_RESERVATION_ID
            );

        assertThat(result.amount())
            .isEqualTo(
                PORTONE_PAYMENT_AMOUNT
            );

        assertThat(result.paymentStatus())
            .isEqualTo(
                PaymentStatus.PAID
            );

        assertThat(result.reservationStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(result.approvedAt())
            .isEqualTo(
                PORTONE_PAID_AT
            );

        then(paymentService)
            .should(times(1))
            .findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(paymentService)
            .should(times(1))
            .findForPaymentTransitionForUpdate(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(portOneClient)
            .should()
            .getPayment(
                PORTONE_PAYMENT_ID
            );

        then(portOnePaymentVerifier)
            .should()
            .verify(
                portOneResponse,
                PORTONE_PAYMENT_ID,
                PORTONE_PAYMENT_AMOUNT
            );

        then(paymentService)
            .should()
            .approvePortOnePayment(
                paymentForUpdate,
                PORTONE_TRANSACTION_ID,
                PORTONE_PAYMENT_AMOUNT,
                PORTONE_PAID_AT
            );

        then(reservationService)
            .should()
            .confirmPayment(
                portOneReservation,
                PORTONE_MEMBER_ID,
                PORTONE_PAID_AT
            );
    }

    @Test
    @DisplayName(
        "PortOne 결제 검증에 실패하면 락 조회와 DB 상태 변경을 실행하지 않는다"
    )
    void doNotUpdatePaymentWhenPortOneVerificationFails() {

        // given
        Payment paymentSnapshot =
            org.mockito.Mockito.mock(
                Payment.class
            );

        PortOnePaymentResponseDto portOneResponse =
            createPaidPortOneResponse();

        given(
            paymentService.findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            )
        ).willReturn(
            paymentSnapshot
        );

        given(
            paymentSnapshot.getPortOnePaymentId()
        ).willReturn(
            PORTONE_PAYMENT_ID
        );

        given(
            paymentSnapshot.getAmount()
        ).willReturn(
            PORTONE_PAYMENT_AMOUNT
        );

        given(
            portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        ).willReturn(
            portOneResponse
        );

        given(
            portOnePaymentVerifier.verify(
                portOneResponse,
                PORTONE_PAYMENT_ID,
                PORTONE_PAYMENT_AMOUNT
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.PORTONE_PAYMENT_NOT_PAID
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentFacade.completePortOnePayment(
                        PORTONE_PAYMENT_INTERNAL_ID,
                        PORTONE_MEMBER_ID
                    ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PORTONE_PAYMENT_NOT_PAID
            );

        then(paymentService)
            .should(times(1))
            .findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(paymentService)
            .should(never())
            .findForPaymentTransitionForUpdate(
                anyLong(),
                anyLong()
            );

        verifyNoInteractions(
            transactionTemplate
        );

        then(paymentService)
            .should(never())
            .approvePortOnePayment(
                any(Payment.class),
                any(String.class),
                anyLong(),
                any(LocalDateTime.class)
            );

        verifyNoInteractions(
            reservationService
        );
    }

    @Test
    @DisplayName(
        "다른 회원의 결제이면 PortOne 외부 API와 락 조회를 호출하지 않는다"
    )
    void doNotCallPortOneWhenPaymentOwnerIsDifferent() {

        // given
        given(
            paymentService.findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_ACCESS_DENIED
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentFacade.completePortOnePayment(
                        PORTONE_PAYMENT_INTERNAL_ID,
                        PORTONE_MEMBER_ID
                    ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_ACCESS_DENIED
            );

        verifyNoInteractions(
            portOneClient,
            portOnePaymentVerifier,
            transactionTemplate,
            reservationService
        );

        then(paymentService)
            .should(never())
            .findForPaymentTransitionForUpdate(
                anyLong(),
                anyLong()
            );

        then(paymentService)
            .should(never())
            .approvePortOnePayment(
                any(Payment.class),
                any(String.class),
                anyLong(),
                any(LocalDateTime.class)
            );
    }

    @Test
    @DisplayName(
        "READY 상태가 아닌 결제이면 PortOne 외부 API와 락 조회를 호출하지 않는다"
    )
    void doNotCallPortOneWhenPaymentIsNotReady() {

        // given
        given(
            paymentService.findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentFacade.completePortOnePayment(
                        PORTONE_PAYMENT_INTERNAL_ID,
                        PORTONE_MEMBER_ID
                    ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        verifyNoInteractions(
            portOneClient,
            portOnePaymentVerifier,
            transactionTemplate,
            reservationService
        );

        then(paymentService)
            .should(never())
            .findForPaymentTransitionForUpdate(
                anyLong(),
                anyLong()
            );

        then(paymentService)
            .should(never())
            .approvePortOnePayment(
                any(Payment.class),
                any(String.class),
                anyLong(),
                any(LocalDateTime.class)
            );
    }

    @Test
    @DisplayName(
        "PortOne 조회 전후 결제 식별값이 다르면 락 획득 후 완료 처리를 거절한다"
    )
    void rejectWhenPortOnePaymentIdChanges() {

        // given
        stubTransactionTemplate();

        Payment paymentSnapshot =
            org.mockito.Mockito.mock(
                Payment.class
            );

        Payment paymentForUpdate =
            org.mockito.Mockito.mock(
                Payment.class
            );

        PortOnePaymentResponseDto portOneResponse =
            createPaidPortOneResponse();

        PortOnePaymentVerificationResultDto
            verificationResult =
            createVerificationResult();

        given(
            paymentService.findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            )
        ).willReturn(
            paymentSnapshot
        );

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    PORTONE_PAYMENT_INTERNAL_ID,
                    PORTONE_MEMBER_ID
                )
        ).willReturn(
            paymentForUpdate
        );

        given(
            paymentSnapshot.getPortOnePaymentId()
        ).willReturn(
            PORTONE_PAYMENT_ID
        );

        given(
            paymentSnapshot.getAmount()
        ).willReturn(
            PORTONE_PAYMENT_AMOUNT
        );

        given(
            portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        ).willReturn(
            portOneResponse
        );

        given(
            portOnePaymentVerifier.verify(
                portOneResponse,
                PORTONE_PAYMENT_ID,
                PORTONE_PAYMENT_AMOUNT
            )
        ).willReturn(
            verificationResult
        );

        given(
            paymentForUpdate.getPortOnePaymentId()
        ).willReturn(
            "changed-payment-id"
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentFacade.completePortOnePayment(
                        PORTONE_PAYMENT_INTERNAL_ID,
                        PORTONE_MEMBER_ID
                    ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PORTONE_PAYMENT_ID_MISMATCH
            );

        then(paymentService)
            .should(times(1))
            .findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(paymentService)
            .should(times(1))
            .findForPaymentTransitionForUpdate(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(paymentService)
            .should(never())
            .approvePortOnePayment(
                any(Payment.class),
                any(String.class),
                anyLong(),
                any(LocalDateTime.class)
            );

        verifyNoInteractions(
            reservationService
        );
    }

    @Test
    @DisplayName(
        "PortOne 검증 후 락을 획득한 결제가 이미 처리된 상태이면 예약을 확정하지 않는다"
    )
    void doNotConfirmReservationWhenLockedPaymentIsNotReady() {

        // given
        stubTransactionTemplate();

        Payment paymentSnapshot =
            org.mockito.Mockito.mock(
                Payment.class
            );

        Payment paymentForUpdate =
            org.mockito.Mockito.mock(
                Payment.class
            );

        PortOnePaymentResponseDto portOneResponse =
            createPaidPortOneResponse();

        PortOnePaymentVerificationResultDto
            verificationResult =
            createVerificationResult();

        given(
            paymentService.findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            )
        ).willReturn(
            paymentSnapshot
        );

        /*
         * 공통 락 조회 메서드는 READY 상태를 검증하지 않고
         * 최신 Payment를 반환합니다.
         */
        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    PORTONE_PAYMENT_INTERNAL_ID,
                    PORTONE_MEMBER_ID
                )
        ).willReturn(
            paymentForUpdate
        );

        given(
            paymentSnapshot.getPortOnePaymentId()
        ).willReturn(
            PORTONE_PAYMENT_ID
        );

        given(
            paymentSnapshot.getAmount()
        ).willReturn(
            PORTONE_PAYMENT_AMOUNT
        );

        given(
            portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        ).willReturn(
            portOneResponse
        );

        given(
            portOnePaymentVerifier.verify(
                portOneResponse,
                PORTONE_PAYMENT_ID,
                PORTONE_PAYMENT_AMOUNT
            )
        ).willReturn(
            verificationResult
        );

        given(
            paymentForUpdate.getPortOnePaymentId()
        ).willReturn(
            PORTONE_PAYMENT_ID
        );

        /*
         * 외부 API를 조회하는 사이 다른 요청이 결제를
         * 먼저 처리한 상황을 가정합니다.
         *
         * 공통 락 조회가 아니라 실제 Payment 상태 전이에서
         * INVALID_PAYMENT_STATUS가 발생해야 합니다.
         */
        given(
            paymentService.approvePortOnePayment(
                paymentForUpdate,
                PORTONE_TRANSACTION_ID,
                PORTONE_PAYMENT_AMOUNT,
                PORTONE_PAID_AT
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentFacade.completePortOnePayment(
                        PORTONE_PAYMENT_INTERNAL_ID,
                        PORTONE_MEMBER_ID
                    ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );

        then(paymentService)
            .should(times(1))
            .findForPortOneCompletion(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(paymentService)
            .should(times(1))
            .findForPaymentTransitionForUpdate(
                PORTONE_PAYMENT_INTERNAL_ID,
                PORTONE_MEMBER_ID
            );

        then(paymentService)
            .should(times(1))
            .approvePortOnePayment(
                paymentForUpdate,
                PORTONE_TRANSACTION_ID,
                PORTONE_PAYMENT_AMOUNT,
                PORTONE_PAID_AT
            );

        verifyNoInteractions(
            reservationService
        );
    }

    /**
     * TransactionTemplate에 전달된 콜백을
     * 단위 테스트에서 즉시 실행하도록 설정합니다.
     *
     * 트랜잭션 구간에 진입하는 테스트에서만 호출합니다.
     */
    private void stubTransactionTemplate() {
        given(
            transactionTemplate.execute(
                org.mockito.ArgumentMatchers
                    .<TransactionCallback<
                        PaymentCompleteResponseDto
                        >>any()
            )
        ).willAnswer(invocation -> {
            TransactionCallback<
                PaymentCompleteResponseDto
                > callback =
                invocation.getArgument(0);

            return callback.doInTransaction(
                transactionStatus
            );
        });
    }

    private PortOnePaymentResponseDto
    createPaidPortOneResponse() {

        return new PortOnePaymentResponseDto(
            "PAID",
            PORTONE_PAYMENT_ID,
            PORTONE_TRANSACTION_ID,
            new PortOnePaymentResponseDto.Amount(
                PORTONE_PAYMENT_AMOUNT
            ),
            OffsetDateTime.of(
                2026,
                7,
                29,
                8,
                0,
                0,
                0,
                ZoneOffset.UTC
            )
        );
    }

    private PortOnePaymentVerificationResultDto
    createVerificationResult() {

        return new PortOnePaymentVerificationResultDto(
            PORTONE_TRANSACTION_ID,
            PORTONE_PAYMENT_AMOUNT,
            PORTONE_PAID_AT
        );
    }
}
