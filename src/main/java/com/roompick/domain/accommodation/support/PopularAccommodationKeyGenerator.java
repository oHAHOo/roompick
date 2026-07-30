package com.roompick.domain.accommodation.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * 인기 숙소 랭킹에서 사용하는 Redis 키를 생성합니다.
 *
 * 날짜 계산 기준을 Asia/Seoul로 고정하고,
 * 일간 랭킹 키 규칙을 한곳에서 관리합니다.
 */
@Component
public class PopularAccommodationKeyGenerator {

    private static final String DAILY_KEY_PREFIX =
        "roompick:popular:accommodations:daily:";

    private static final ZoneId SEOUL_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    /**
     * 현재 서울 날짜를 기준으로 일간 인기 숙소 키를 생성합니다.
     *
     * 예:
     * roompick:popular:accommodations:daily:2026-07-29
     */
    public String generateTodayKey() {
        LocalDate today = LocalDate.now(
            SEOUL_ZONE_ID
        );

        return generateDailyKey(
            today
        );
    }

    /**
     * 전달받은 날짜를 기준으로 일간 인기 숙소 키를 생성합니다.
     *
     * 날짜를 직접 받는 메서드를 분리해 두면
     * 날짜 변경 테스트와 이후 과거 랭킹 조회에서도 재사용할 수 있습니다.
     */
    public String generateDailyKey(
        LocalDate date
    ) {
        Objects.requireNonNull(
            date,
            "인기 숙소 랭킹의 기준 날짜는 null일 수 없습니다."
        );

        return DAILY_KEY_PREFIX + date;
    }
}
