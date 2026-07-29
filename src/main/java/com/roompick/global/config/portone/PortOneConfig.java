package com.roompick.global.config.portone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * PortOne V2 REST API 호출에 사용하는
 * RestClient를 설정합니다.
 */
@Configuration
public class PortOneConfig {

    /**
     * PortOne API 기본 주소와 인증 헤더가 설정된
     * RestClient를 Spring Bean으로 등록합니다.
     */
    @Bean
    public RestClient portOneRestClient(
        RestClient.Builder builder,
        PortOneProperties properties
    ) {
        return builder
            .baseUrl(
                properties.api().baseUrl()
            )
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "PortOne "
                    + properties.api().secret()
            )
            .defaultHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .build();
    }
}
