package com.roompick.global.config.place;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 장소 검색 API 연결 설정입니다.
 */
@ConfigurationProperties(prefix = "place.api")
public record PlaceSearchProperties(
    String baseUrl,
    String key,
    Duration connectTimeout,
    Duration readTimeout
) {
}
