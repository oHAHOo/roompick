package com.roompick.domain.timesale.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.entity.TimeSaleStatus;
import com.roompick.domain.timesale.repository.TimeSaleRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TimeSaleService {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private final TimeSaleRepository timeSaleRepository;
    private final Clock clock;

    /**
     * 숙소 전체 또는 특정 객실에 적용할
     * 타임세일을 등록합니다.
     */
    @Transactional
    public TimeSale create(
        Accommodation accommodation,
        Room room,
        int discountRate,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        LocalDateTime now = now();

        /*
         * Repository를 조회하기 전에 Entity를 생성해
         * 대상, 할인율, 기간 등의 입력값을 먼저 검증합니다.
         */
        TimeSale timeSale = TimeSale.create(
            accommodation,
            room,
            discountRate,
            startAt,
            endAt,
            now
        );

        validatePeriodNotOverlapping(
            accommodation,
            room,
            startAt,
            endAt
        );

        return timeSaleRepository.save(timeSale);
    }

    /**
     * 시작 시각에 도달한 SCHEDULED 타임세일을
     * ACTIVE 상태로 변경합니다.
     */
    @Transactional
    public int activateDueSales() {
        LocalDateTime now = now();

        List<TimeSale> targets =
            timeSaleRepository.findStartTargets(
                TimeSaleStatus.SCHEDULED,
                now
            );

        targets.forEach(
            target -> target.activate(now)
        );

        return targets.size();
    }

    /**
     * 종료 시각에 도달한 SCHEDULED 또는 ACTIVE
     * 타임세일을 ENDED 상태로 변경합니다.
     *
     * 시작·종료 스케줄 사이에 실행이 누락된 타임세일도
     * 종료할 수 있도록 SCHEDULED 상태를 함께 조회합니다.
     */
    @Transactional
    public int endDueSales() {
        LocalDateTime now = now();

        List<TimeSale> targets =
            timeSaleRepository.findEndTargets(
                List.of(
                    TimeSaleStatus.SCHEDULED,
                    TimeSaleStatus.ACTIVE
                ),
                now
            );

        targets.forEach(
            target -> target.end(now)
        );

        return targets.size();
    }

    /**
     * 동일한 대상에 기간이 겹치는 타임세일이
     * 존재하는지 확인합니다.
     */
    private void validatePeriodNotOverlapping(
        Accommodation accommodation,
        Room room,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        boolean overlapping;

        if (room == null) {
            overlapping =
                timeSaleRepository
                    .existsOverlappingAccommodationSale(
                        accommodation.getId(),
                        startAt,
                        endAt
                    );
        } else {
            overlapping =
                timeSaleRepository
                    .existsOverlappingRoomSale(
                        room.getId(),
                        startAt,
                        endAt
                    );
        }

        if (overlapping) {
            throw new BusinessException(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP
            );
        }
    }

    /**
     * 애플리케이션의 Clock을 기준으로
     * 한국 시간의 현재 시각을 반환합니다.
     */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
            clock.instant(),
            SERVICE_ZONE_ID
        );
    }
}
