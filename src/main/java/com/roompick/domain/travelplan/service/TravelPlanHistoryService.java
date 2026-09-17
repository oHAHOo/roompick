package com.roompick.domain.travelplan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.travelplan.dto.RecommendedAccommodationDto;
import com.roompick.domain.travelplan.entity.AiRecommendation;
import com.roompick.domain.travelplan.repository.AiRecommendationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 생성된 여행 계획 이력을 저장하는 Service입니다.
 *
 * LLM 호출이 끝난 뒤에만 호출되므로
 * 외부 API 호출과 DB 트랜잭션이 겹치지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanHistoryService {

    private final AiRecommendationRepository aiRecommendationRepository;

    /**
     * 여행 계획과 추천 숙소 목록을 저장하고 저장된 계획 ID를 반환합니다.
     */
    @Transactional
    public Long save(
        double latitude,
        double longitude,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        String llmModel,
        String itineraryJson,
        List<RecommendedAccommodationDto> recommendations
    ) {
        AiRecommendation recommendation =
            AiRecommendation.create(
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude),
                checkInDate,
                checkOutDate,
                guestCount,
                llmModel,
                itineraryJson
            );

        for (int index = 0; index < recommendations.size(); index++) {
            RecommendedAccommodationDto item =
                recommendations.get(index);

            recommendation.addItem(
                item.accommodationId(),
                index,
                item.reason()
            );
        }

        return aiRecommendationRepository
            .save(recommendation)
            .getId();
    }
}
