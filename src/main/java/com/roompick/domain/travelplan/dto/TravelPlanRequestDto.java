package com.roompick.domain.travelplan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 여행 계획 생성 요청 정보를 전달하는 DTO입니다.
 *
 * 좌표는 장소 검색 API(GET /api/v1/places/search)로 미리 확보한 값을 사용합니다.
 * 날짜 순서와 과거 여부는 Service에서 검증합니다.
 */
public record TravelPlanRequestDto(

    @NotNull(message = "위도는 필수입니다.")
    Double latitude,

    @NotNull(message = "경도는 필수입니다.")
    Double longitude,

    @NotNull(message = "체크인 날짜는 필수입니다.")
    LocalDate checkInDate,

    @NotNull(message = "체크아웃 날짜는 필수입니다.")
    LocalDate checkOutDate,

    /*
     * Integer를 사용해야 요청에서 값이 누락됐을 때
     * null 여부를 구분하고 @NotNull 검증을 적용할 수 있습니다.
     */
    @NotNull(message = "여행 인원은 필수입니다.")
    @Min(value = 1, message = "여행 인원은 1명 이상이어야 합니다.")
    Integer guestCount
) {
}
