package com.roompick.domain.accommodation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

class PopularAccommodationKeyGeneratorTest {

    @Test
    void DAILY는_서울_현재_날짜를_기준으로_키를_생성한다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-02T15:30:00Z"
        );

        assertThat(
            keyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.DAILY
            )
        ).isEqualTo(
            "roompick:popular:accommodations:daily:2026-08-03"
        );
    }

    @Test
    void 일요일의_WEEKLY_기준일은_직전_월요일이다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-02T03:00:00Z"
        );

        assertThat(
            keyGenerator.getCurrentBaseDate(
                PopularAccommodationPeriod.WEEKLY
            )
        ).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(
            keyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.WEEKLY
            )
        ).isEqualTo(
            "roompick:popular:accommodations:weekly:2026-07-27"
        );
    }

    @Test
    void 월요일의_WEEKLY_기준일은_당일이다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-03T03:00:00Z"
        );

        assertThat(
            keyGenerator.getCurrentBaseDate(
                PopularAccommodationPeriod.WEEKLY
            )
        ).isEqualTo(LocalDate.of(2026, 8, 3));
    }

    @Test
    void 일요일에서_월요일로_넘어가면_WEEKLY_키가_변경된다() {
        String sundayKey = keyGeneratorAt(
            "2026-08-02T14:59:59Z"
        ).generateCurrentKey(PopularAccommodationPeriod.WEEKLY);
        String mondayKey = keyGeneratorAt(
            "2026-08-02T15:00:00Z"
        ).generateCurrentKey(PopularAccommodationPeriod.WEEKLY);

        assertThat(sundayKey).endsWith("2026-07-27");
        assertThat(mondayKey).endsWith("2026-08-03");
    }

    @Test
    void 기간별_키는_서로_분리된다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-03T03:00:00Z"
        );

        assertThat(
            keyGenerator.generateKey(
                PopularAccommodationPeriod.DAILY,
                LocalDate.of(2026, 8, 3)
            )
        ).isNotEqualTo(
            keyGenerator.generateKey(
                PopularAccommodationPeriod.WEEKLY,
                LocalDate.of(2026, 8, 3)
            )
        );
    }

    @Test
    void generateKey의_WEEKLY_일요일은_직전_월요일로_보정한다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-02T03:00:00Z"
        );

        assertThat(
            keyGenerator.generateKey(
                PopularAccommodationPeriod.WEEKLY,
                LocalDate.of(2026, 8, 2)
            )
        ).isEqualTo(
            "roompick:popular:accommodations:weekly:2026-07-27"
        );
    }

    @Test
    void generateKey의_WEEKLY_월요일은_당일을_유지한다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-03T03:00:00Z"
        );

        assertThat(
            keyGenerator.generateKey(
                PopularAccommodationPeriod.WEEKLY,
                LocalDate.of(2026, 8, 3)
            )
        ).isEqualTo(
            "roompick:popular:accommodations:weekly:2026-08-03"
        );
    }

    @Test
    void generateKey의_DAILY는_전달된_날짜를_유지한다() {
        PopularAccommodationKeyGenerator keyGenerator = keyGeneratorAt(
            "2026-08-03T03:00:00Z"
        );

        assertThat(
            keyGenerator.generateKey(
                PopularAccommodationPeriod.DAILY,
                LocalDate.of(2026, 8, 2)
            )
        ).isEqualTo(
            "roompick:popular:accommodations:daily:2026-08-02"
        );
    }

    private PopularAccommodationKeyGenerator keyGeneratorAt(
        String instant
    ) {
        return new PopularAccommodationKeyGenerator(
            Clock.fixed(
                Instant.parse(instant),
                ZoneOffset.UTC
            )
        );
    }
}
