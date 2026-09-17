package com.roompick.domain.travelplan.dto;

import java.util.List;

import com.roompick.domain.travelplan.model.TravelPlanLlmResult;

/**
 * 여행 일정 하루치를 반환하는 DTO입니다.
 */
public record ItineraryDayDto(
    int day,
    String summary,
    List<String> activities
) {

    public static ItineraryDayDto from(
        TravelPlanLlmResult.ItineraryDay itineraryDay
    ) {
        return new ItineraryDayDto(
            itineraryDay.day(),
            itineraryDay.summary(),
            itineraryDay.activities()
        );
    }
}
