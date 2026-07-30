package com.roompick.global.config.portone;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * PortOne V2 REST API 호출에 사용하는
 * RestClient를 설정합니다.
 */
@Configuration
public class PortOneConfig {

    /**
     * PortOne API 호출에 사용하는
     * 연결 및 응답 타임아웃이 설정된
     * RestClient를 Spring Bean으로 등록합니다.
     */
    @Bean
    public RestClient portOneRestClient(
        RestClient.Builder builder,
        PortOneProperties properties
    ) {
        PortOneProperties.Api apiProperties =
            properties.api();

        HttpClient httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    apiProperties.connectTimeout()
                )
                .build();

        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(
                httpClient
            );

        requestFactory.setReadTimeout(
            apiProperties.readTimeout()
        );

        return builder
            .requestFactory(requestFactory)
            .baseUrl(
                apiProperties.baseUrl()
            )
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "PortOne "
                    + apiProperties.secret()
            )
            .defaultHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .build();
    }
}
