package com.roompick.global.config.cache;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인기 숙소 Single Flight의 운영 설정을 타입 안전하게 제공합니다.
 *
 * 기본 대기 시간 5초는 로컬·운영 Redis 연결 및 응답 제한 300ms보다 충분히 길면서,
 * 원본 조회가 비정상적으로 지연될 때 HTTP 요청 스레드의 무제한 대기를 방지하는 값입니다.
 */
@ConfigurationProperties(
    prefix = "roompick.cache.popular-accommodations.single-flight"
)
public record PopularAccommodationSingleFlightProperties(
    @DefaultValue("5s") Duration waitTimeout
) {

    public PopularAccommodationSingleFlightProperties {
        if (
            waitTimeout == null
                || waitTimeout.isNegative()
                || waitTimeout.isZero()
        ) {
            throw new IllegalArgumentException(
                "인기 숙소 Single Flight 대기 시간은 0보다 커야 합니다."
            );
        }
    }
}
