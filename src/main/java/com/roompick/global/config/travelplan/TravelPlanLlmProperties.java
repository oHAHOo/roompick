package com.roompick.global.config.travelplan;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 여행 계획 생성에 사용하는 LLM 연결 설정입니다.
 */
@ConfigurationProperties(prefix = "llm.anthropic")
public record TravelPlanLlmProperties(
    String apiKey,
    String model,
    Duration timeout
) {
}
