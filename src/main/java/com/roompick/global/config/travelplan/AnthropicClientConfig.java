package com.roompick.global.config.travelplan;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * 여행 계획 생성에 사용하는 Anthropic Claude API 클라이언트를 설정합니다.
 */
@Configuration
public class AnthropicClientConfig {

    /**
     * API 키와 응답 타임아웃이 설정된 Anthropic 클라이언트를 등록합니다.
     */
    @Bean
    public AnthropicClient anthropicClient(
        TravelPlanLlmProperties properties
    ) {
        return AnthropicOkHttpClient.builder()
            .apiKey(properties.apiKey())
            .timeout(properties.timeout())
            .build();
    }
}
