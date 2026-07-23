package com.roompick.domain.reservation.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.room.entity.Room;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 생성한 객실 예약을 나타내는 Entity입니다.
 *
 * 예약 당시의 객실 가격과 숙박 일수, 총액을 저장하여
 * 이후 객실 가격이 변경되어도 기존 예약 금액이 바뀌지 않게 합니다.
 */
@Getter
@Entity
@Table(
    name = "reservations",
    indexes = {
        @Index(
            name = "idx_reservations_member_created_at",
            columnList = "member_id, created_at"
        ),
        @Index(
            name = "idx_reservations_room_status_stay_period",
            columnList = "room_id, status, check_in_date, check_out_date"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    // 예약을 생성한 회원입니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 실제로 예약되는 물리적 객실입니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    // 예약 생성 당시 객실의 1박 가격을 저장합니다.
    @Column(name = "price_per_night", nullable = false)
    private long pricePerNight;

    @Column(name = "night_count", nullable = false)
    private int nightCount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    // PENDING_PAYMENT 예약이 결제를 기다릴 수 있는 만료 시각입니다.
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /**
     * 결제 대기 상태의 새로운 예약을 생성합니다.
     */
    public static Reservation create(
        Member member,
        Room room,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        LocalDateTime expiresAt
    ) {
        validateMember(member);
        validateRoom(room);
        validateStayPeriod(checkInDate, checkOutDate);
        validateGuestCount(room, guestCount);
        validateExpiresAt(expiresAt);

        int nightCount = Math.toIntExact(
            ChronoUnit.DAYS.between(checkInDate, checkOutDate)
        );
        long pricePerNight = room.getPricePerNight();
        long totalAmount = Math.multiplyExact(pricePerNight, nightCount);

        return new Reservation(
            member,
            room,
            checkInDate,
            checkOutDate,
            guestCount,
            pricePerNight,
            nightCount,
            totalAmount,
            ReservationStatus.PENDING_PAYMENT,
            expiresAt
        );
    }

    private Reservation(
        Member member,
        Room room,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        long pricePerNight,
        int nightCount,
        long totalAmount,
        ReservationStatus status,
        LocalDateTime expiresAt
    ) {
        this.member = member;
        this.room = room;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.guestCount = guestCount;
        this.pricePerNight = pricePerNight;
        this.nightCount = nightCount;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    private static void validateMember(Member member) {
        if (member == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void validateRoom(Room room) {
        if (room == null) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
    }

    private static void validateStayPeriod(
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        if (
            checkInDate == null
                || checkOutDate == null
                || !checkInDate.isBefore(checkOutDate)
        ) {
            throw new BusinessException(ErrorCode.INVALID_STAY_PERIOD);
        }
    }

    private static void validateGuestCount(Room room, int guestCount) {
        if (guestCount < 1) {
            throw new BusinessException(ErrorCode.INVALID_GUEST_COUNT);
        }

        if (guestCount > room.getMaxCapacity()) {
            throw new BusinessException(ErrorCode.ROOM_CAPACITY_EXCEEDED);
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
