package com.roompick.global.config.portone;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
    Api api,
    String storeId,
    String channelKey
) {

    public record Api(
        String baseUrl,
        String secret
    ) {
    }
}
