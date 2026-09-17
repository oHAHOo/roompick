package com.roompick.domain.travelplan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.roompick.global.common.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LLM이 생성한 여행 계획 한 건의 이력을 나타내는 Entity입니다.
 *
 * 일정은 구조화된 JSON 문자열로 저장하고,
 * 추천된 숙소는 AiRecommendationItem으로 분리해 저장합니다.
 */
@Getter
@Entity
@Table(name = "ai_recommendations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_recommendation_id")
    private Long id;

    @Column(precision = 9, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6, nullable = false)
    private BigDecimal longitude;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    @Column(name = "llm_model", nullable = false, length = 50)
    private String llmModel;

    @Column(name = "itinerary_json", nullable = false, columnDefinition = "TEXT")
    private String itineraryJson;

    @OneToMany(
        mappedBy = "aiRecommendation",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("rankOrder ASC")
    private List<AiRecommendationItem> items = new ArrayList<>();

    public static AiRecommendation create(
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        String llmModel,
        String itineraryJson
    ) {
        AiRecommendation recommendation = new AiRecommendation();
        recommendation.latitude = latitude;
        recommendation.longitude = longitude;
        recommendation.checkInDate = checkInDate;
        recommendation.checkOutDate = checkOutDate;
        recommendation.guestCount = guestCount;
        recommendation.llmModel = llmModel;
        recommendation.itineraryJson = itineraryJson;
        return recommendation;
    }

    /**
     * 추천된 숙소를 순위와 추천 이유와 함께 추가합니다.
     */
    public void addItem(
        Long accommodationId,
        int rankOrder,
        String reason
    ) {
        items.add(
            AiRecommendationItem.create(
                this,
                accommodationId,
                rankOrder,
                reason
            )
        );
    }

    public List<AiRecommendationItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
