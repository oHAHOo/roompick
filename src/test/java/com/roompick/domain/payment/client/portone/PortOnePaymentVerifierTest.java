package com.roompick.domain.payment.client.portone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentResponseDto;
import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentVerificationResultDto;
import com.roompick.global.common.BusinessException;

class PortOnePaymentVerifierTest {

    private static final String PORTONE_PAYMENT_ID =
        "roompick-payment-test-001";

    private static final String TRANSACTION_ID =
        "transaction-test-001";

    private static final long PAYMENT_AMOUNT =
        200_000L;

    private final PortOnePaymentVerifier verifier =
        new PortOnePaymentVerifier();

    @Test
    @DisplayName("PortOne 결제 정보가 모두 일치하면 검증에 성공한다")
    void verifyPaidPaymentSuccessfully() {

        // given
        OffsetDateTime paidAt =
            OffsetDateTime.of(
                2026,
                7,
                29,
                8,
                0,
                0,
                0,
                ZoneOffset.UTC
            );

        PortOnePaymentResponseDto response =
            createResponse(
                "PAID",
                PORTONE_PAYMENT_ID,
                TRANSACTION_ID,
                PAYMENT_AMOUNT,
                paidAt
            );

        // when
        PortOnePaymentVerificationResultDto result =
            verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            );

        // then
        assertEquals(
            TRANSACTION_ID,
            result.transactionId()
        );

        assertEquals(
            PAYMENT_AMOUNT,
            result.amount()
        );

        /*
         * UTC 08:00은 Asia/Seoul 기준 17:00입니다.
         */
        assertEquals(
            LocalDateTime.of(
                2026,
                7,
                29,
                17,
                0
            ),
            result.paidAt()
        );
    }

    @Test
    @DisplayName("PortOne 결제 ID가 다르면 검증에 실패한다")
    void rejectWhenPaymentIdDoesNotMatch() {

        // given
        PortOnePaymentResponseDto response =
            createValidResponse(
                "different-payment-id"
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    @Test
    @DisplayName("PortOne 결제 상태가 PAID가 아니면 검증에 실패한다")
    void rejectWhenPaymentIsNotPaid() {

        // given
        PortOnePaymentResponseDto response =
            createResponse(
                "READY",
                PORTONE_PAYMENT_ID,
                TRANSACTION_ID,
                PAYMENT_AMOUNT,
                validPaidAt()
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    @Test
    @DisplayName("PortOne 결제 금액이 다르면 검증에 실패한다")
    void rejectWhenPaymentAmountDoesNotMatch() {

        // given
        PortOnePaymentResponseDto response =
            createResponse(
                "PAID",
                PORTONE_PAYMENT_ID,
                TRANSACTION_ID,
                190_000L,
                validPaidAt()
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    @Test
    @DisplayName("PortOne 거래 식별값이 없으면 검증에 실패한다")
    void rejectWhenTransactionIdIsMissing() {

        // given
        PortOnePaymentResponseDto response =
            createResponse(
                "PAID",
                PORTONE_PAYMENT_ID,
                null,
                PAYMENT_AMOUNT,
                validPaidAt()
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    @Test
    @DisplayName("PortOne 결제 완료 시각이 없으면 검증에 실패한다")
    void rejectWhenPaidAtIsMissing() {

        // given
        PortOnePaymentResponseDto response =
            createResponse(
                "PAID",
                PORTONE_PAYMENT_ID,
                TRANSACTION_ID,
                PAYMENT_AMOUNT,
                null
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    @Test
    @DisplayName("PortOne 결제 금액 정보가 없으면 검증에 실패한다")
    void rejectWhenAmountIsMissing() {

        // given
        PortOnePaymentResponseDto response =
            new PortOnePaymentResponseDto(
                "PAID",
                PORTONE_PAYMENT_ID,
                TRANSACTION_ID,
                null,
                validPaidAt()
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                response,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    @Test
    @DisplayName("PortOne 응답이 없으면 검증에 실패한다")
    void rejectWhenResponseIsNull() {

        // when & then
        assertThrows(
            BusinessException.class,
            () -> verifier.verify(
                null,
                PORTONE_PAYMENT_ID,
                PAYMENT_AMOUNT
            )
        );
    }

    private PortOnePaymentResponseDto
    createValidResponse(
        String portOnePaymentId
    ) {

        return createResponse(
            "PAID",
            portOnePaymentId,
            TRANSACTION_ID,
            PAYMENT_AMOUNT,
            validPaidAt()
        );
    }

    private PortOnePaymentResponseDto createResponse(
        String status,
        String portOnePaymentId,
        String transactionId,
        Long amount,
        OffsetDateTime paidAt
    ) {
        PortOnePaymentResponseDto.Amount
            amountResponse = null;

        if (amount != null) {
            amountResponse =
                new PortOnePaymentResponseDto.Amount(
                    amount
                );
        }

        return new PortOnePaymentResponseDto(
            status,
            portOnePaymentId,
            transactionId,
            amountResponse,
            paidAt
        );
    }

    private OffsetDateTime validPaidAt() {
        return OffsetDateTime.of(
            2026,
            7,
            29,
            8,
            0,
            0,
            0,
            ZoneOffset.UTC
        );
    }
}
