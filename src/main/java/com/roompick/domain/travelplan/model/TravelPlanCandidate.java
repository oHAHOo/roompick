package com.roompick.domain.travelplan.model;

/**
 * LLM에 전달할 실제 등록 숙소 후보입니다.
 *
 * LLM은 이 후보 목록 안에서만 숙소를 선택할 수 있으며,
 * 목록에 없는 숙소를 만들어내지 못하게 하는 근거 데이터 역할을 합니다.
 */
public record TravelPlanCandidate(
    Long accommodationId,
    String name,
    String address,
    double distanceKm
) {
}
