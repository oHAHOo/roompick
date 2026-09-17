package com.roompick.domain.travelplan.entity;

import com.roompick.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 계획 한 건에서 추천된 숙소 하나를 나타내는 Entity입니다.
 *
 * rankOrder는 0부터 시작하는 추천 순위이며,
 * reason은 LLM이 제시한 추천 이유입니다.
 *
 * 숙소는 다른 담당자가 소유한 도메인이므로 Accommodation Entity를 직접
 * 연관하지 않고 accommodation_id만 보관합니다. 참조 정합성은 DB FK로 보장합니다.
 */
@Getter
@Entity
@Table(name = "ai_recommendation_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendationItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_recommendation_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_recommendation_id", nullable = false)
    private AiRecommendation aiRecommendation;

    @Column(name = "accommodation_id", nullable = false)
    private Long accommodationId;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    static AiRecommendationItem create(
        AiRecommendation aiRecommendation,
        Long accommodationId,
        int rankOrder,
        String reason
    ) {
        AiRecommendationItem item = new AiRecommendationItem();
        item.aiRecommendation = aiRecommendation;
        item.accommodationId = accommodationId;
        item.rankOrder = rankOrder;
        item.reason = reason;
        return item;
    }
}
