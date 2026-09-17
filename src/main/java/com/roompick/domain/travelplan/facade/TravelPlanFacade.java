package com.roompick.domain.travelplan.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.travelplan.dto.TravelPlanRequestDto;
import com.roompick.domain.travelplan.dto.TravelPlanResponseDto;
import com.roompick.domain.travelplan.service.TravelPlanService;

import lombok.RequiredArgsConstructor;

/**
 * 여행 계획 생성 API 흐름을 조율하는 Facade입니다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanFacade {

    private final TravelPlanService travelPlanService;

    /**
     * 여행 계획 생성 요청을 Service에 전달하고 결과를 반환합니다.
     */
    public TravelPlanResponseDto generatePlan(
        TravelPlanRequestDto request
    ) {
        return travelPlanService.generatePlan(request);
    }
}
