package com.roompick.domain.timesale.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

class TimeSaleTest {

    private static final LocalDateTime NOW =
        LocalDateTime.of(
            2026,
            8,
            12,
            10,
            0
        );

    @Test
    @DisplayName(
        "시작 전 타임세일은 SCHEDULED 상태로 생성된다"
    )
    void createScheduledTimeSale() {
        TimeSale timeSale =
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.plusHours(1),
                NOW.plusHours(2),
                NOW
            );

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.SCHEDULED
            );
    }

    @Test
    @DisplayName(
        "이미 시작 시각에 도달한 타임세일은 ACTIVE 상태로 생성된다"
    )
    void createActiveTimeSale() {
        TimeSale timeSale =
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.minusHours(1),
                NOW.plusHours(1),
                NOW
            );

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );
    }

    @Test
    @DisplayName(
        "타임세일은 시작 시각부터 적용된다"
    )
    void applyFromStartAt() {
        TimeSale timeSale =
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.plusHours(1),
                NOW.plusHours(2),
                NOW
            );

        assertThat(
            timeSale.appliesAt(
                NOW.plusMinutes(59)
            )
        ).isFalse();

        assertThat(
            timeSale.appliesAt(
                NOW.plusHours(1)
            )
        ).isTrue();
    }

    @Test
    @DisplayName(
        "타임세일은 종료 시각부터 적용되지 않는다"
    )
    void doNotApplyFromEndAt() {
        TimeSale timeSale =
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.minusHours(1),
                NOW.plusHours(1),
                NOW
            );

        assertThat(
            timeSale.appliesAt(
                NOW.plusHours(1)
            )
        ).isFalse();
    }

    @Test
    @DisplayName(
        "시작 시각에 도달한 SCHEDULED 타임세일은 ACTIVE로 변경된다"
    )
    void activateScheduledTimeSale() {
        TimeSale timeSale =
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.plusHours(1),
                NOW.plusHours(2),
                NOW
            );

        timeSale.activate(
            NOW.plusHours(1)
        );

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );
    }

    @Test
    @DisplayName(
        "종료 시각에 도달한 타임세일은 ENDED로 변경된다"
    )
    void endTimeSale() {
        TimeSale timeSale =
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.minusHours(1),
                NOW.plusHours(1),
                NOW
            );

        timeSale.end(
            NOW.plusHours(1)
        );

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ENDED
            );

        assertThat(
            timeSale.appliesAt(
                NOW.plusMinutes(30)
            )
        ).isFalse();
    }

    @Test
    @DisplayName(
        "할인율이 1% 미만이면 타임세일을 생성할 수 없다"
    )
    void rejectDiscountRateBelowMinimum() {
        assertThatThrownBy(() ->
            TimeSale.create(
                createAccommodation(1L),
                null,
                0,
                NOW,
                NOW.plusHours(1),
                NOW
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode
                    .INVALID_TIME_SALE_DISCOUNT_RATE
            );
    }

    @Test
    @DisplayName(
        "할인율이 99%를 초과하면 타임세일을 생성할 수 없다"
    )
    void rejectDiscountRateAboveMaximum() {
        assertThatThrownBy(() ->
            TimeSale.create(
                createAccommodation(1L),
                null,
                100,
                NOW,
                NOW.plusHours(1),
                NOW
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode
                    .INVALID_TIME_SALE_DISCOUNT_RATE
            );
    }

    @Test
    @DisplayName(
        "종료 시각이 시작 시각보다 늦지 않으면 생성할 수 없다"
    )
    void rejectInvalidPeriod() {
        assertThatThrownBy(() ->
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.plusHours(1),
                NOW,
                NOW
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_TIME_SALE_PERIOD
            );
    }

    @Test
    @DisplayName(
        "이미 종료된 기간의 타임세일은 생성할 수 없다"
    )
    void rejectAlreadyEndedPeriod() {
        assertThatThrownBy(() ->
            TimeSale.create(
                createAccommodation(1L),
                null,
                20,
                NOW.minusHours(2),
                NOW.minusHours(1),
                NOW
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_TIME_SALE_PERIOD
            );
    }

    @Test
    @DisplayName(
        "다른 숙소의 객실을 타임세일 대상으로 지정할 수 없다"
    )
    void rejectRoomFromDifferentAccommodation() {
        Accommodation targetAccommodation =
            createAccommodation(1L);

        Accommodation roomAccommodation =
            createAccommodation(2L);

        Room room =
            Room.create(
                roomAccommodation,
                "101",
                "디럭스룸",
                null,
                100_000L,
                2,
                2
            );

        assertThatThrownBy(() ->
            TimeSale.create(
                targetAccommodation,
                room,
                20,
                NOW,
                NOW.plusHours(1),
                NOW
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.TIME_SALE_TARGET_MISMATCH
            );
    }

    private Accommodation createAccommodation(
        Long accommodationId
    ) {
        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시",
                null,
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        ReflectionTestUtils.setField(
            accommodation,
            "id",
            accommodationId
        );

        return accommodation;
    }
}
