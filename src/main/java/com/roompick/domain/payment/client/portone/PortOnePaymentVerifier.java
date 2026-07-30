package com.roompick.domain.payment.client.portone;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentResponseDto;
import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentVerificationResultDto;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * PortOne에서 조회한 결제 정보가
 * RoomPick의 결제 정보와 일치하는지 검증합니다.
 */
@Component
public class PortOnePaymentVerifier {

    private static final String PAID_STATUS =
        "PAID";

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    /**
     * PortOne 결제 상태, 결제 ID, 결제 금액과
     * 결제 완료 정보를 검증합니다.
     */
    public PortOnePaymentVerificationResultDto verify(
        PortOnePaymentResponseDto response,
        String expectedPortOnePaymentId,
        long expectedAmount
    ) {
        validateExpectedValues(
            expectedPortOnePaymentId,
            expectedAmount
        );

        validateRequiredResponse(response);

        validatePaymentId(
            response.id(),
            expectedPortOnePaymentId
        );

        validatePaidStatus(
            response.status()
        );

        validateAmount(
            response.amount(),
            expectedAmount
        );

        validateTransactionId(
            response.transactionId()
        );

        validatePaidAt(
            response.paidAt()
        );

        LocalDateTime paidAt =
            response.paidAt()
                .atZoneSameInstant(
                    SERVICE_ZONE_ID
                )
                .toLocalDateTime()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        return new PortOnePaymentVerificationResultDto(
            response.transactionId(),
            response.amount().total(),
            paidAt
        );
    }

    /**
     * RoomPick에 저장된 예상 결제 정보가
     * 정상적인 값인지 검증합니다.
     */
    private void validateExpectedValues(
        String expectedPortOnePaymentId,
        long expectedAmount
    ) {
        if (expectedPortOnePaymentId == null
            || expectedPortOnePaymentId.isBlank()) {

            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }

        if (expectedAmount < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_PAYMENT_AMOUNT
            );
        }
    }

    /**
     * PortOne 응답 자체가 존재하는지 검증합니다.
     */
    private void validateRequiredResponse(
        PortOnePaymentResponseDto response
    ) {
        if (response == null) {
            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }
    }

    /**
     * PortOne에서 반환한 결제 ID가
     * RoomPick에 저장된 결제 ID와 같은지 검증합니다.
     */
    private void validatePaymentId(
        String actualPortOnePaymentId,
        String expectedPortOnePaymentId
    ) {
        if (actualPortOnePaymentId == null
            || actualPortOnePaymentId.isBlank()) {

            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }

        if (!expectedPortOnePaymentId.equals(
            actualPortOnePaymentId
        )) {
            throw new BusinessException(
                ErrorCode.PORTONE_PAYMENT_ID_MISMATCH
            );
        }
    }

    /**
     * PortOne 결제 상태가 PAID인지 검증합니다.
     */
    private void validatePaidStatus(
        String status
    ) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }

        if (!PAID_STATUS.equals(status)) {
            throw new BusinessException(
                ErrorCode.PORTONE_PAYMENT_NOT_PAID
            );
        }
    }

    /**
     * PortOne 결제 금액이
     * RoomPick에 저장된 결제 금액과 같은지 검증합니다.
     */
    private void validateAmount(
        PortOnePaymentResponseDto.Amount amount,
        long expectedAmount
    ) {
        if (amount == null
            || amount.total() == null) {

            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }

        if (amount.total() != expectedAmount) {
            throw new BusinessException(
                ErrorCode.PORTONE_PAYMENT_AMOUNT_MISMATCH
            );
        }
    }

    /**
     * PortOne 거래 식별값이 존재하는지 검증합니다.
     */
    private void validateTransactionId(
        String transactionId
    ) {
        if (transactionId == null
            || transactionId.isBlank()) {

            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }
    }

    /**
     * PortOne 결제 완료 시각이 존재하는지 검증합니다.
     */
    private void validatePaidAt(
        java.time.OffsetDateTime paidAt
    ) {
        if (paidAt == null) {
            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }
    }
}
