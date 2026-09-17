package com.roompick.domain.travelplan.client;

import com.roompick.domain.travelplan.model.TravelPlanLlmRequest;
import com.roompick.domain.travelplan.model.TravelPlanLlmResult;

/**
 * LLM으로 여행 계획과 숙소 추천을 생성하는 Client 계약입니다.
 *
 * 실제 LLM 제공자와 통신 방식은 구현체 내부에 격리합니다.
 */
public interface TravelPlanLlmClient {

    /**
     * 여행 조건과 실제 숙소 후보를 전달해 일정과 추천 숙소를 생성합니다.
     */
    TravelPlanLlmResult generate(
        TravelPlanLlmRequest request
    );

    /**
     * 이력 저장에 사용할 현재 LLM 모델 식별자를 반환합니다.
     */
    String modelName();
}
