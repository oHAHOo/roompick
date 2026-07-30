package com.roompick.global.config.portone;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
    Api api,
    String storeId,
    String channelKey
) {

    public record Api(
        String baseUrl,
        String secret,
        Duration connectTimeout,
        Duration readTimeout
    ) {
    }
}
