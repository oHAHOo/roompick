package com.roompick.domain.payment.entity;

import java.time.LocalDateTime;

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
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

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
     * 결제 금액은 외부 요청값이 아니라 예약에 저장된 총액을 사용합니다.
     */
    public static Payment create(
        Reservation reservation
    ) {
        validateReservation(reservation);

        long amount = reservation.getTotalAmount();

        validateAmount(amount);

        return new Payment(
            reservation,
            amount,
            PaymentStatus.READY
        );
    }

    private Payment(
        Reservation reservation,
        long amount,
        PaymentStatus status
    ) {
        this.reservation = reservation;
        this.amount = amount;
        this.status = status;
    }

    /**
     * READY 상태의 결제를 승인 완료 상태로 변경합니다.
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

    private static void validateApprovedAt(
        LocalDateTime approvedAt
    ) {
        if (approvedAt == null) {
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
