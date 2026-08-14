package com.roompick.global.config.place;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Kakao Local 장소 검색 API 호출에 사용하는
 * RestClient를 설정합니다.
 */
@Configuration
public class PlaceSearchClientConfig {

    /**
     * Kakao Local API 연결 및 응답 타임아웃과
     * REST API 키 인증 헤더가 설정된 RestClient를 등록합니다.
     */
    @Bean
    public RestClient kakaoPlaceRestClient(
        RestClient.Builder builder,
        PlaceSearchProperties properties
    ) {
        HttpClient httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    properties.connectTimeout()
                )
                .build();

        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(
                httpClient
            );

        requestFactory.setReadTimeout(
            properties.readTimeout()
        );

        return builder
            .requestFactory(requestFactory)
            .baseUrl(
                properties.baseUrl()
            )
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "KakaoAK "
                    + properties.key()
            )
            .defaultHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .build();
    }
}
