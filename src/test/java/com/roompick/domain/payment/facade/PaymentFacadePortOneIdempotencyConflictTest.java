package com.roompick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.roompick.domain.payment.dto.response.PaymentCompleteResponseDto;
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
class PaymentFacadePortOneIdempotencyConflictTest {

    private static final Long PAYMENT_ID =
        100L;

    private static final Long MEMBER_ID =
        10L;

    private static final String PORTONE_PAYMENT_ID =
        "roompick-payment-idempotency-001";

    private static final String STORED_TRANSACTION_ID =
        "transaction-stored-001";

    private static final long STORED_AMOUNT =
        200_000L;

    private static final LocalDateTime STORED_PAID_AT =
        LocalDateTime.of(
            2026,
            8,
            3,
            15,
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
    private Payment paymentSnapshot;

    @Mock
    private Payment paymentForUpdate;

    @Mock
    private Reservation reservation;

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName(
        "PortOne 검증 후 락 획득 시 기존 거래 ID와 다르면 "
            + "멱등성 충돌을 반환한다"
    )
    void differentTransactionIdAfterLockReturnsIdempotencyConflict() {

        // given
        PortOnePaymentVerificationResultDto verificationResult =
            new PortOnePaymentVerificationResultDto(
                "transaction-different-002",
                STORED_AMOUNT,
                STORED_PAID_AT
            );

        stubCompletedPaymentAfterExternalVerification(
            verificationResult
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.completePortOnePayment(
                    PAYMENT_ID,
                    MEMBER_ID
                ),
                BusinessException.class
            );

        // then
        assertIdempotencyConflictWithoutStateTransition(
            exception
        );
    }

    @Test
    @DisplayName(
        "PortOne 검증 후 락 획득 시 기존 결제 금액과 다르면 "
            + "멱등성 충돌을 반환한다"
    )
    void differentAmountAfterLockReturnsIdempotencyConflict() {

        // given
        PortOnePaymentVerificationResultDto verificationResult =
            new PortOnePaymentVerificationResultDto(
                STORED_TRANSACTION_ID,
                STORED_AMOUNT - 10_000L,
                STORED_PAID_AT
            );

        stubCompletedPaymentAfterExternalVerification(
            verificationResult
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.completePortOnePayment(
                    PAYMENT_ID,
                    MEMBER_ID
                ),
                BusinessException.class
            );

        // then
        assertIdempotencyConflictWithoutStateTransition(
            exception
        );
    }

    @Test
    @DisplayName(
        "PortOne 검증 후 락 획득 시 기존 승인 시각과 다르면 "
            + "멱등성 충돌을 반환한다"
    )
    void differentPaidAtAfterLockReturnsIdempotencyConflict() {

        // given
        PortOnePaymentVerificationResultDto verificationResult =
            new PortOnePaymentVerificationResultDto(
                STORED_TRANSACTION_ID,
                STORED_AMOUNT,
                STORED_PAID_AT.plusSeconds(1)
            );

        stubCompletedPaymentAfterExternalVerification(
            verificationResult
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.completePortOnePayment(
                    PAYMENT_ID,
                    MEMBER_ID
                ),
                BusinessException.class
            );

        // then
        assertIdempotencyConflictWithoutStateTransition(
            exception
        );
    }

    private void stubCompletedPaymentAfterExternalVerification(
        PortOnePaymentVerificationResultDto verificationResult
    ) {
        PortOnePaymentResponseDto portOneResponse =
            createPaidPortOneResponse();

        stubTransactionTemplate();

        given(
            paymentService.findForPortOneCompletion(
                PAYMENT_ID,
                MEMBER_ID
            )
        ).willReturn(
            paymentSnapshot
        );

        given(paymentSnapshot.getReservation())
            .willReturn(reservation);

        given(paymentSnapshot.getStatus())
            .willReturn(PaymentStatus.READY);

        given(paymentSnapshot.getPortOnePaymentId())
            .willReturn(PORTONE_PAYMENT_ID);

        given(paymentSnapshot.getAmount())
            .willReturn(STORED_AMOUNT);

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
                STORED_AMOUNT
            )
        ).willReturn(
            verificationResult
        );

        given(
            paymentService
                .findForPaymentTransitionForUpdate(
                    PAYMENT_ID,
                    MEMBER_ID
                )
        ).willReturn(
            paymentForUpdate
        );

        given(paymentForUpdate.getReservation())
            .willReturn(reservation);

        given(paymentForUpdate.getStatus())
            .willReturn(PaymentStatus.PAID);

        given(paymentForUpdate.getPortOnePaymentId())
            .willReturn(PORTONE_PAYMENT_ID);

        given(paymentForUpdate.getPortOneTransactionId())
            .willReturn(STORED_TRANSACTION_ID);

        given(paymentForUpdate.getAmount())
            .willReturn(STORED_AMOUNT);

        given(paymentForUpdate.getApprovedAt())
            .willReturn(STORED_PAID_AT);

        given(reservation.getStatus())
            .willReturn(ReservationStatus.CONFIRMED);
    }

    private void assertIdempotencyConflictWithoutStateTransition(
        BusinessException exception
    ) {
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT
            );

        then(paymentService)
            .should(times(1))
            .findForPortOneCompletion(
                PAYMENT_ID,
                MEMBER_ID
            );

        then(paymentService)
            .should(times(1))
            .findForPaymentTransitionForUpdate(
                PAYMENT_ID,
                MEMBER_ID
            );

        then(paymentService)
            .should(never())
            .approvePortOnePayment(
                eq(paymentForUpdate),
                any(String.class),
                anyLong(),
                any(LocalDateTime.class)
            );

        then(reservationService)
            .should(never())
            .confirmPayment(
                eq(reservation),
                eq(MEMBER_ID),
                any(LocalDateTime.class)
            );
    }

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
            STORED_TRANSACTION_ID,
            new PortOnePaymentResponseDto.Amount(
                STORED_AMOUNT
            ),
            OffsetDateTime.of(
                2026,
                8,
                3,
                6,
                0,
                0,
                0,
                ZoneOffset.UTC
            )
        );
    }
}
