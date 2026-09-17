package com.roompick.domain.travelplan.model;

import java.util.List;

/**
 * LLM이 생성한 여행 계획 결과입니다.
 *
 * selections의 candidateIndex는 요청에 전달한 후보 목록의 인덱스이며,
 * Service가 실제 숙소 데이터와 매핑할 때 사용합니다.
 */
public record TravelPlanLlmResult(
    List<ItineraryDay> itinerary,
    List<CandidateSelection> selections
) {

    /**
     * 여행 일정 하루치입니다.
     */
    public record ItineraryDay(
        int day,
        String summary,
        List<String> activities
    ) {
    }

    /**
     * LLM이 선택한 후보 숙소와 그 추천 이유입니다.
     */
    public record CandidateSelection(
        int candidateIndex,
        String reason
    ) {
    }
}
