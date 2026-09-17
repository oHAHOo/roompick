package com.roompick.domain.travelplan.client.anthropic.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Claude가 반환하는 여행 계획 JSON 본문 구조입니다.
 *
 * recommendations의 candidateIndex는 요청에 전달한 후보 목록의 인덱스입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeTravelPlanPayloadDto(
    List<ClaudeItineraryDayDto> itinerary,
    List<ClaudeRecommendationDto> recommendations
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClaudeItineraryDayDto(
        Integer day,
        String summary,
        List<String> activities
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClaudeRecommendationDto(
        Integer candidateIndex,
        String reason
    ) {
    }
}
