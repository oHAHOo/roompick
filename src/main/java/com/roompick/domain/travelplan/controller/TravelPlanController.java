package com.roompick.domain.travelplan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.travelplan.dto.TravelPlanRequestDto;
import com.roompick.domain.travelplan.dto.TravelPlanResponseDto;
import com.roompick.domain.travelplan.facade.TravelPlanFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 좌표와 숙박 기간으로 여행 계획과 추천 숙소를 생성하는 공개 API를 제공합니다.
 *
 * 좌표는 장소 검색 API(GET /api/v1/places/search)로 미리 확보해 전달합니다.
 */
@RestController
@RequestMapping("/api/v1/travel-plans")
@RequiredArgsConstructor
public class TravelPlanController {

    private final TravelPlanFacade travelPlanFacade;

    /**
     * 여행 일정과 등록된 숙소 중 계획에 맞는 추천 숙소를 반환합니다.
     */
    @PostMapping
    public ResponseEntity<
        ApiResponseDto<TravelPlanResponseDto>
        > generateTravelPlan(
        @Valid
        @RequestBody
        TravelPlanRequestDto request
    ) {
        TravelPlanResponseDto result =
            travelPlanFacade.generatePlan(request);

        ResponseEntity<
            ApiResponseDto<TravelPlanResponseDto>
            > response =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "여행 계획 생성에 성공했습니다.",
                        result
                    )
                );

        return response;
    }
}
