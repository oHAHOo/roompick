package com.roompick.domain.travelplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roompick.domain.travelplan.entity.AiRecommendation;

/**
 * LLM 여행 계획 생성 이력을 저장하는 Repository입니다.
 */
public interface AiRecommendationRepository
    extends JpaRepository<AiRecommendation, Long> {
}
