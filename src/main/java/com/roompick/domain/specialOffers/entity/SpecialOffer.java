package com.roompick.domain.specialOffers.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "special_offers",
    uniqueConstraints = @UniqueConstraint(columnNames = "room_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SpecialOffer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "special_offer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private long price;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpecialOfferStatus status;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    private SpecialOffer(Room room, long price, LocalDateTime startsAt, LocalDateTime endsAt, SpecialOfferStatus status, LocalDate checkInDate, LocalDate checkOutDate) {
        this.room = room;
        this.price = price;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
    }

    public static SpecialOffer create(Room room, long price, LocalDateTime startsAt, LocalDateTime endsAt, LocalDate checkInDate, LocalDate checkOutDate) {
        validateRoom(room);
        validatePrice(price);
        validatePeriod(startsAt, endsAt);
        validateStayPeriod(checkInDate, checkOutDate);

        return new SpecialOffer(room, price, startsAt, endsAt, SpecialOfferStatus.SCHEDULED, checkInDate, checkOutDate);
    }

    private static void validateStayPeriod(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void validateRoom(Room room) {
        if (room == null) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
    }

    private static void validatePrice(long price) {
        if (price <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public void activate(LocalDateTime now) {
        if (now != null
            && status == SpecialOfferStatus.SCHEDULED
            && !now.isBefore(startsAt)
            && now.isBefore(endsAt)) {
            status = SpecialOfferStatus.ACTIVE;
        }
    }

    public void end(LocalDateTime now) {
        if (now != null
            && status != SpecialOfferStatus.ENDED
            && !now.isBefore(endsAt)) {
            status = SpecialOfferStatus.ENDED;
        }
    }

    /**
     * 현재 시각에 점유 및 대기열 승계가 가능한 특가인지 확인합니다.
     *
     * 상태 갱신 스케줄러가 아직 ENDED를 반영하지 못했더라도
     * 종료 시각이 지났다면 새로운 HOLD를 만들 수 없습니다.
     */
    public boolean isActiveAt(LocalDateTime now) {
        return now != null
            && status == SpecialOfferStatus.ACTIVE
            && !now.isBefore(startsAt)
            && now.isBefore(endsAt);
    }
}
