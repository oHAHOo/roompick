package com.roompick.domain.travelplan.model;

import java.time.LocalDate;
import java.util.List;

/**
 * LLM에 전달하는 여행 계획 생성 요청입니다.
 *
 * candidates가 비어 있으면 숙소 추천 없이 일정만 생성합니다.
 */
public record TravelPlanLlmRequest(
    double latitude,
    double longitude,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int guestCount,
    List<TravelPlanCandidate> candidates
) {
}
