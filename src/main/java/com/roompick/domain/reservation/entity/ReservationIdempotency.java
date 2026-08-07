package com.roompick.domain.reservation.entity;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.roompick.domain.member.entity.Member;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약 생성 요청의 멱등성 처리 정보를 나타냅니다.
 *
 * 최초 요청에서는 PROCESSING 상태로 생성되고,
 * 예약 생성이 완료되면 생성된 예약과 연결한 뒤
 * COMPLETED 상태로 변경됩니다.
 *
 * 최초 처리 정보는 동일 키의 동시 요청을 제어하기 위해
 * Repository의 원자적인 INSERT 쿼리로 생성합니다.
 */
@Getter
@Entity
@Table(
    name = "reservation_idempotencies",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_reservation_idempotencies_member_key",
            columnNames = {
                "member_id",
                "idempotency_key"
            }
        ),
        @UniqueConstraint(
            name = "uk_reservation_idempotencies_reservation",
            columnNames = "reservation_id"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationIdempotency
    extends BaseTimeEntity {

    @Id
    @GeneratedValue(
        strategy = GenerationType.IDENTITY
    )
    @Column(
        name = "reservation_idempotency_id"
    )
    private Long id;

    /**
     * 멱등성 키를 사용한 회원입니다.
     *
     * 같은 키 문자열이라도 회원이 다르면
     * 서로 독립적인 요청으로 처리합니다.
     */
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "member_id",
        nullable = false
    )
    private Member member;

    /**
     * 클라이언트가 전달한 멱등성 키입니다.
     */
    @Column(
        name = "idempotency_key",
        nullable = false,
        length = 100
    )
    private String idempotencyKey;

    /**
     * 객실 ID, 숙박 기간, 예약 인원을 기준으로
     * 생성한 SHA-256 요청 해시입니다.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "request_hash",
        nullable = false,
        length = 64,
        columnDefinition = "CHAR(64)"
    )
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private ReservationIdempotencyStatus status;

    /**
     * 최초 요청으로 생성된 예약입니다.
     *
     * PROCESSING 상태에서는 null이고,
     * COMPLETED 상태에서만 예약이 연결됩니다.
     */
    @OneToOne(
        fetch = FetchType.LAZY,
        optional = true
    )
    @JoinColumn(
        name = "reservation_id",
        unique = true
    )
    @Getter(AccessLevel.NONE)
    private Reservation reservation;

    /**
     * 저장된 요청 해시가 현재 요청 해시와 같은지 확인합니다.
     */
    public boolean matchesRequestHash(
        String requestHash
    ) {
        return Objects.equals(
            this.requestHash,
            requestHash
        );
    }

    /**
     * 최초 예약 생성 처리가 완료됐는지 확인합니다.
     */
    public boolean isCompleted() {
        return status
            == ReservationIdempotencyStatus.COMPLETED;
    }

    /**
     * 최초 요청으로 생성된 예약을 연결하고
     * 멱등성 처리를 완료 상태로 변경합니다.
     */
    public void complete(
        Reservation reservation
    ) {
        if (reservation == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }

        if (
            status
                != ReservationIdempotencyStatus.PROCESSING
                || this.reservation != null
        ) {
            throw new IllegalStateException(
                "이미 완료된 예약 멱등성 요청입니다."
            );
        }

        this.reservation = reservation;
        this.status =
            ReservationIdempotencyStatus.COMPLETED;
    }

    /**
     * 완료된 최초 요청의 예약 결과를 반환합니다.
     */
    public Reservation getCompletedReservation() {
        if (
            !isCompleted()
                || reservation == null
        ) {
            throw new IllegalStateException(
                "예약 멱등성 처리가 완료되지 않았습니다."
            );
        }

        return reservation;
    }
}
