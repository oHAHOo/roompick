package com.roompick.domain.timesale.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.roompick.domain.accommodation.entity.Accommodation;
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
 * 숙소 또는 특정 객실에 적용되는 타임세일입니다.
 *
 * room이 null이면 숙소 전체 타임세일이고,
 * room이 존재하면 해당 객실에만 적용됩니다.
 *
 * 상태 갱신은 스케줄러가 담당하지만 실제 할인 적용 여부는
 * 상태뿐 아니라 시작·종료 시각을 함께 확인합니다.
 */
@Getter
@Entity
@Table(
    name = "time_sales",
    indexes = {
        @Index(
            name = "idx_time_sales_start_status",
            columnList = "status, start_at"
        ),
        @Index(
            name = "idx_time_sales_end_status",
            columnList = "status, end_at"
        ),
        @Index(
            name = "idx_time_sales_accommodation_period",
            columnList =
                "accommodation_id, start_at, end_at"
        ),
        @Index(
            name = "idx_time_sales_room_period",
            columnList =
                "room_id, start_at, end_at"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeSale extends BaseTimeEntity {

    private static final int MIN_DISCOUNT_RATE = 1;
    private static final int MAX_DISCOUNT_RATE = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_sale_id")
    private Long id;

    /**
     * 타임세일 대상 숙소입니다.
     *
     * 객실 타임세일인 경우에도 객실이 소속된 숙소를
     * 함께 저장합니다.
     */
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "accommodation_id",
        nullable = false
    )
    private Accommodation accommodation;

    /**
     * 타임세일 대상 객실입니다.
     *
     * null이면 숙소 전체에 적용되는 타임세일입니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(
        name = "discount_rate",
        nullable = false
    )
    private int discountRate;

    @Column(
        name = "start_at",
        nullable = false
    )
    private LocalDateTime startAt;

    @Column(
        name = "end_at",
        nullable = false
    )
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private TimeSaleStatus status;

    /**
     * 새로운 타임세일을 생성합니다.
     *
     * 시작 시각이 현재 시각보다 이후이면 SCHEDULED,
     * 이미 시작 시각에 도달했다면 ACTIVE 상태로 생성합니다.
     */
    public static TimeSale create(
        Accommodation accommodation,
        Room room,
        int discountRate,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime now
    ) {
        validateAccommodation(accommodation);
        validateTarget(accommodation, room);
        validateDiscountRate(discountRate);
        validatePeriod(startAt, endAt, now);

        TimeSale timeSale = new TimeSale();

        timeSale.accommodation = accommodation;
        timeSale.room = room;
        timeSale.discountRate = discountRate;
        timeSale.startAt = startAt;
        timeSale.endAt = endAt;
        timeSale.status = startAt.isAfter(now)
            ? TimeSaleStatus.SCHEDULED
            : TimeSaleStatus.ACTIVE;

        return timeSale;
    }

    /**
     * 특정 시각에 이 타임세일을 실제로 적용할 수 있는지
     * 확인합니다.
     *
     * 시작 시각은 포함하고 종료 시각은 포함하지 않습니다.
     */
    public boolean appliesAt(LocalDateTime now) {
        if (now == null) {
            return false;
        }

        return status != TimeSaleStatus.ENDED
            && !now.isBefore(startAt)
            && now.isBefore(endAt);
    }

    /**
     * 시작 시각에 도달한 예약 상태의 타임세일을
     * 활성 상태로 변경합니다.
     */
    public void activate(LocalDateTime now) {
        if (
            now != null
                && status == TimeSaleStatus.SCHEDULED
                && !now.isBefore(startAt)
                && now.isBefore(endAt)
        ) {
            status = TimeSaleStatus.ACTIVE;
        }
    }

    /**
     * 종료 시각에 도달한 타임세일을 종료 상태로
     * 변경합니다.
     */
    public void end(LocalDateTime now) {
        if (
            now != null
                && status != TimeSaleStatus.ENDED
                && !now.isBefore(endAt)
        ) {
            status = TimeSaleStatus.ENDED;
        }
    }

    private static void validateAccommodation(
        Accommodation accommodation
    ) {
        if (accommodation == null) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_NOT_FOUND
            );
        }
    }

    /**
     * 객실 타임세일이라면 객실이 요청한 숙소에
     * 소속되어 있는지 확인합니다.
     */
    private static void validateTarget(
        Accommodation accommodation,
        Room room
    ) {
        if (room == null) {
            return;
        }

        Accommodation roomAccommodation =
            room.getAccommodation();

        if (
            roomAccommodation == null
                || !Objects.equals(
                roomAccommodation.getId(),
                accommodation.getId()
            )
        ) {
            throw new BusinessException(
                ErrorCode.TIME_SALE_TARGET_MISMATCH
            );
        }
    }

    private static void validateDiscountRate(
        int discountRate
    ) {
        if (
            discountRate < MIN_DISCOUNT_RATE
                || discountRate > MAX_DISCOUNT_RATE
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_TIME_SALE_DISCOUNT_RATE
            );
        }
    }

    private static void validatePeriod(
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime now
    ) {
        if (
            startAt == null
                || endAt == null
                || now == null
                || !endAt.isAfter(startAt)
                || !endAt.isAfter(now)
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_TIME_SALE_PERIOD
            );
        }
    }
}
