package com.roompick.domain.timesale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.repository.TimeSaleRepository;

@ExtendWith(MockitoExtension.class)
class TimeSalePriceServiceTest {

    @Mock
    private TimeSaleRepository
        timeSaleRepository;

    private TimeSalePriceService
        timeSalePriceService;

    private Accommodation accommodation;

    private Room room;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        Instant instant =
            Instant.parse(
                "2026-08-12T01:00:00Z"
            );

        Clock clock =
            Clock.fixed(
                instant,
                ZoneOffset.UTC
            );

        timeSalePriceService =
            new TimeSalePriceService(
                timeSaleRepository,
                clock
            );

        now = LocalDateTime.of(
            2026,
            8,
            12,
            10,
            0
        );

        accommodation =
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
            1L
        );

        room = Room.create(
            accommodation,
            "101",
            "디럭스룸",
            null,
            100_000L,
            2,
            2
        );

        ReflectionTestUtils.setField(
            room,
            "id",
            10L
        );
    }

    @Test
    @DisplayName(
        "객실 전용 타임세일이 있으면 할인 가격을 반환한다"
    )
    void applyRoomTimeSale() {
        TimeSale roomTimeSale =
            TimeSale.create(
                accommodation,
                room,
                20,
                now.minusHours(1),
                now.plusHours(1),
                now
            );

        given(
            timeSaleRepository
                .findApplicableRoomSales(
                    room.getId(),
                    now
                )
        ).willReturn(
            List.of(roomTimeSale)
        );

        long result =
            timeSalePriceService
                .calculatePricePerNight(
                    room
                );

        assertThat(result)
            .isEqualTo(80_000L);

        verify(
            timeSaleRepository,
            never()
        ).findApplicableAccommodationSales(
            accommodation.getId(),
            now
        );
    }

    @Test
    @DisplayName(
        "객실 타임세일이 없으면 숙소 전체 타임세일을 적용한다"
    )
    void applyAccommodationTimeSale() {
        TimeSale accommodationTimeSale =
            TimeSale.create(
                accommodation,
                null,
                15,
                now.minusHours(1),
                now.plusHours(1),
                now
            );

        given(
            timeSaleRepository
                .findApplicableRoomSales(
                    room.getId(),
                    now
                )
        ).willReturn(
            List.of()
        );

        given(
            timeSaleRepository
                .findApplicableAccommodationSales(
                    accommodation.getId(),
                    now
                )
        ).willReturn(
            List.of(accommodationTimeSale)
        );

        long result =
            timeSalePriceService
                .calculatePricePerNight(
                    room
                );

        assertThat(result)
            .isEqualTo(85_000L);
    }

    @Test
    @DisplayName(
        "객실과 숙소 타임세일이 없으면 정상 가격을 반환한다"
    )
    void returnNormalPriceWithoutTimeSale() {
        given(
            timeSaleRepository
                .findApplicableRoomSales(
                    room.getId(),
                    now
                )
        ).willReturn(
            List.of()
        );

        given(
            timeSaleRepository
                .findApplicableAccommodationSales(
                    accommodation.getId(),
                    now
                )
        ).willReturn(
            List.of()
        );

        long result =
            timeSalePriceService
                .calculatePricePerNight(
                    room
                );

        assertThat(result)
            .isEqualTo(100_000L);
    }

    @Test
    @DisplayName(
        "할인 가격의 소수점 이하는 버림 처리한다"
    )
    void truncateDiscountedPrice() {
        Room oddPriceRoom =
            Room.create(
                accommodation,
                "102",
                "스탠다드룸",
                null,
                99_999L,
                2,
                2
            );

        ReflectionTestUtils.setField(
            oddPriceRoom,
            "id",
            11L
        );

        TimeSale timeSale =
            TimeSale.create(
                accommodation,
                oddPriceRoom,
                20,
                now.minusHours(1),
                now.plusHours(1),
                now
            );

        given(
            timeSaleRepository
                .findApplicableRoomSales(
                    oddPriceRoom.getId(),
                    now
                )
        ).willReturn(
            List.of(timeSale)
        );

        long result =
            timeSalePriceService
                .calculatePricePerNight(
                    oddPriceRoom
                );

        assertThat(result)
            .isEqualTo(79_999L);
    }
}
