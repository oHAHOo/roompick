package com.roompick.domain.travelplan.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 생성된 여행 계획과 추천 숙소를 반환하는 DTO입니다.
 *
 * 반경 안에 등록된 숙소가 없으면 recommendedAccommodations는 빈 배열입니다.
 */
public record TravelPlanResponseDto(
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int guestCount,
    List<ItineraryDayDto> itinerary,
    List<RecommendedAccommodationDto> recommendedAccommodations
) {
}
