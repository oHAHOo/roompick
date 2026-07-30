package com.roompick.domain.accommodation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * PopularAccommodationKeyGenerator의
 * 일간 인기 숙소 Redis 키 생성 규칙을 검증합니다.
 */
class PopularAccommodationKeyGeneratorTest {

    private final PopularAccommodationKeyGenerator keyGenerator =
        new PopularAccommodationKeyGenerator();

    @Test
    void 날짜를_전달하면_일간_인기_숙소_키를_생성한다() {
        // given
        LocalDate date = LocalDate.of(
            2026,
            7,
            29
        );

        // when
        String key = keyGenerator.generateDailyKey(
            date
        );

        // then
        assertThat(key).isEqualTo(
            "roompick:popular:accommodations:daily:2026-07-29"
        );
    }

    @Test
    void 날짜가_null이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(
            () -> keyGenerator.generateDailyKey(null)
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                "인기 숙소 랭킹의 기준 날짜는 null일 수 없습니다."
            );
    }
}
