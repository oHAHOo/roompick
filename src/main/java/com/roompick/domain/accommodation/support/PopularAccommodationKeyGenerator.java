package com.roompick.domain.accommodation.support;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

import lombok.RequiredArgsConstructor;

/**
 * 인기 숙소 랭킹에서 사용하는 Redis 키를 생성합니다.
 *
 * 날짜 계산 기준을 Asia/Seoul로 고정하고,
 * 기간별 기준 날짜 계산과 랭킹 키 규칙을 한곳에서 관리합니다.
 */
@Component
@RequiredArgsConstructor
public class PopularAccommodationKeyGenerator {

    private static final String DAILY_KEY_PREFIX =
        "roompick:popular:accommodations:daily:";

    private static final String WEEKLY_KEY_PREFIX =
        "roompick:popular:accommodations:weekly:";

    private static final ZoneId SEOUL_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private final Clock clock;

    /**
     * 현재 서울 날짜를 기간별 기준 날짜로 보정하여 키를 생성합니다.
     *
     * 예:
     * roompick:popular:accommodations:daily:2026-07-29
     */
    public String generateCurrentKey(
        PopularAccommodationPeriod period
    ) {
        return generateKey(
            period,
            LocalDate.now(
                clock.withZone(SEOUL_ZONE_ID)
            )
        );
    }

    /**
     * 전달받은 날짜를 기간별 기준 날짜로 보정하여 인기 숙소 키를 생성합니다.
     *
     * 날짜를 직접 받는 메서드를 분리해 두면
     * 날짜 변경 테스트와 이후 과거 랭킹 조회에서도 재사용할 수 있습니다.
     */
    public String generateKey(
        PopularAccommodationPeriod period,
        LocalDate date
    ) {
        Objects.requireNonNull(
            period,
            "인기 숙소 랭킹의 기간은 null일 수 없습니다."
        );

        Objects.requireNonNull(
            date,
            "인기 숙소 랭킹의 기준 날짜는 null일 수 없습니다."
        );

        String keyPrefix = switch (period) {
            case DAILY -> DAILY_KEY_PREFIX;
            case WEEKLY -> WEEKLY_KEY_PREFIX;
        };

        return keyPrefix + resolveBaseDate(
            period,
            date
        );
    }

    /**
     * 서울 현재 날짜를 기간별 랭킹 버킷의 기준 날짜로 변환합니다.
     */
    public LocalDate getCurrentBaseDate(
        PopularAccommodationPeriod period
    ) {
        Objects.requireNonNull(
            period,
            "인기 숙소 랭킹의 기간은 null일 수 없습니다."
        );

        return resolveBaseDate(
            period,
            LocalDate.now(
                clock.withZone(SEOUL_ZONE_ID)
            )
        );
    }

    private LocalDate resolveBaseDate(
        PopularAccommodationPeriod period,
        LocalDate date
    ) {
        if (period == PopularAccommodationPeriod.WEEKLY) {
            return date.with(
                TemporalAdjusters.previousOrSame(
                    DayOfWeek.MONDAY
                )
            );
        }

        return date;
    }
}
