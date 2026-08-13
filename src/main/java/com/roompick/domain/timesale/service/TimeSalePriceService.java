package com.roompick.domain.timesale.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.repository.TimeSaleRepository;

import lombok.RequiredArgsConstructor;

/**
 * 현재 시각에 적용되는 타임세일을 조회하고
 * 객실의 실제 1박 가격을 계산합니다.
 */
@Service
@RequiredArgsConstructor
public class TimeSalePriceService {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private static final long PERCENT_BASE = 100L;

    private final TimeSaleRepository
        timeSaleRepository;

    private final Clock clock;

    /**
     * 현재 시각에 적용할 객실의 1박 가격을
     * 계산합니다.
     *
     * 객실 전용 타임세일을 먼저 확인하고,
     * 없으면 숙소 전체 타임세일을 적용합니다.
     */
    @Transactional(readOnly = true)
    public long calculatePricePerNight(
        Room room
    ) {
        long normalPricePerNight =
            room.getPricePerNight();

        LocalDateTime now = now();

        TimeSale applicableSale =
            findApplicableSale(
                room,
                now
            );

        /*
         * Repository가 실제 기간을 조건으로 조회하지만
         * 스케줄러 상태 지연이나 조회 조건 변경에 대비해
         * Entity에서도 실제 적용 기간을 다시 확인합니다.
         */
        if (
            applicableSale == null
                || !applicableSale.appliesAt(now)
        ) {
            return normalPricePerNight;
        }

        return calculateDiscountedPrice(
            normalPricePerNight,
            applicableSale.getDiscountRate()
        );
    }

    /**
     * 객실 전용 타임세일을 숙소 전체
     * 타임세일보다 우선 적용합니다.
     */
    private TimeSale findApplicableSale(
        Room room,
        LocalDateTime now
    ) {
        List<TimeSale> roomSales =
            timeSaleRepository
                .findApplicableRoomSales(
                    room.getId(),
                    now
                );

        if (!roomSales.isEmpty()) {
            return roomSales.get(0);
        }

        Long accommodationId =
            room.getAccommodation().getId();

        List<TimeSale> accommodationSales =
            timeSaleRepository
                .findApplicableAccommodationSales(
                    accommodationId,
                    now
                );

        if (accommodationSales.isEmpty()) {
            return null;
        }

        return accommodationSales.get(0);
    }

    /**
     * 정수 연산으로 할인 가격을 계산합니다.
     *
     * 소수점 이하는 버림 처리합니다.
     */
    private long calculateDiscountedPrice(
        long normalPricePerNight,
        int discountRate
    ) {
        return normalPricePerNight
            * (PERCENT_BASE - discountRate)
            / PERCENT_BASE;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
            clock.instant(),
            SERVICE_ZONE_ID
        );
    }
}
