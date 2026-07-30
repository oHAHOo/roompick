package com.roompick.domain.payment.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BaseTimeEntity;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약에 대한 결제 정보를 나타내는 Entity입니다.
 *
 * MVP에서는 하나의 예약에 하나의 결제만 생성합니다.
 */
@Getter
@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payments_reservation_id",
            columnNames = "reservation_id"
        ),
        @UniqueConstraint(
            name = "uk_payments_portone_payment_id",
            columnNames = "portone_payment_id"
        ),
        @UniqueConstraint(
            name = "uk_payments_portone_transaction_id",
            columnNames = "portone_transaction_id"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    private static final String PORTONE_PAYMENT_ID_PREFIX =
        "roompick-payment-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    /**
     * 결제 대상 예약입니다.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reservation_id",
        nullable = false
    )
    private Reservation reservation;

    /**
     * PortOne 결제 요청과 조회에 사용하는 결제 식별값입니다.
     */
    @Column(
        name = "portone_payment_id",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String portOnePaymentId;

    /**
     * PortOne에서 결제 완료 후 발급하는 거래 식별값입니다.
     *
     * 결제 준비 단계에서는 값이 없으며,
     * PortOne 결제 검증이 완료된 뒤 저장됩니다.
     */
    @Column(
        name = "portone_transaction_id",
        length = 100
    )
    private String portOneTransactionId;

    /**
     * 예약 생성 시 저장된 총 결제 금액입니다.
     */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /**
     * 예약을 기준으로 결제 준비 정보를 생성합니다.
     *
     * 결제 금액은 외부 요청값이 아니라
     * 예약에 저장된 총액을 사용합니다.
     */
    public static Payment create(
        Reservation reservation
    ) {
        validateReservation(reservation);

        long amount =
            reservation.getTotalAmount();

        validateAmount(amount);

        String portOnePaymentId =
            generatePortOnePaymentId();

        return new Payment(
            reservation,
            portOnePaymentId,
            amount,
            PaymentStatus.READY
        );
    }

    private Payment(
        Reservation reservation,
        String portOnePaymentId,
        long amount,
        PaymentStatus status
    ) {
        this.reservation = reservation;
        this.portOnePaymentId = portOnePaymentId;
        this.amount = amount;
        this.status = status;
    }

    /**
     * 기존 Mock 결제 승인 메서드입니다.
     *
     * PortOne 연동 전 기능을 유지하기 위해
     * 실제 결제 완료 API 구현 전까지 남겨둡니다.
     */
    public void approve(
        long requestedAmount,
        LocalDateTime approvedAt
    ) {
        validateReadyStatus();
        validateRequestedAmount(requestedAmount);
        validateApprovedAt(approvedAt);

        this.status = PaymentStatus.PAID;
        this.approvedAt = approvedAt;
    }

    /**
     * PortOne에서 검증된 결제 정보를 기준으로
     * 결제를 승인 완료 상태로 변경합니다.
     */
    public void approveWithPortOne(
        String portOneTransactionId,
        long verifiedAmount,
        LocalDateTime approvedAt
    ) {
        validateReadyStatus();

        validatePortOneTransactionId(
            portOneTransactionId
        );

        validateRequestedAmount(
            verifiedAmount
        );

        validateApprovedAt(
            approvedAt
        );

        this.portOneTransactionId =
            portOneTransactionId;

        this.status =
            PaymentStatus.PAID;

        this.approvedAt =
            approvedAt;
    }

    /**
     * READY 상태의 결제를 실패 상태로 변경합니다.
     */
    public void fail(LocalDateTime failedAt) {
        validateReadyStatus();
        validateFailedAt(failedAt);

        this.status = PaymentStatus.FAILED;
        this.failedAt = failedAt;
    }

    /**
     * PortOne에서 사용할 고유한 결제 ID를 생성합니다.
     */
    private static String generatePortOnePaymentId() {
        return PORTONE_PAYMENT_ID_PREFIX
            + UUID.randomUUID();
    }

    private void validateReadyStatus() {
        if (status != PaymentStatus.READY) {
            throw new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            );
        }
    }

    private void validateRequestedAmount(
        long requestedAmount
    ) {
        if (amount != requestedAmount) {
            throw new BusinessException(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }
    }

    private static void validatePortOneTransactionId(
        String portOneTransactionId
    ) {
        if (portOneTransactionId == null
            || portOneTransactionId.isBlank()) {

            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    private static void validateApprovedAt(
        LocalDateTime approvedAt
    ) {
        if (approvedAt == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    private static void validateFailedAt(
        LocalDateTime failedAt
    ) {
        if (failedAt == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    private static void validateReservation(
        Reservation reservation
    ) {
        if (reservation == null) {
            throw new BusinessException(
                ErrorCode.RESERVATION_NOT_FOUND
            );
        }
    }

    private static void validateAmount(long amount) {
        if (amount < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_PAYMENT_AMOUNT
            );
        }
    }
}
