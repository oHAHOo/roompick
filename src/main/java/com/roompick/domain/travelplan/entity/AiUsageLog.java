package com.roompick.domain.travelplan.entity;

import com.roompick.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LLM 호출 1건의 비용·지연을 기록하는 감사 로그 Entity입니다.
 *
 * 생성된 일정과 추천 이유는 저장하지 않습니다.
 * 요청 좌표도 남기지 않으므로 어떤 사용자가 어디를 조회했는지는 복원할 수 없습니다.
 */
@Getter
@Entity
@Table(name = "ai_usage_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_usage_log_id")
    private Long id;

    @Column(name = "llm_model", nullable = false, length = 50)
    private String llmModel;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    /**
     * LLM 호출에 걸린 시간입니다.
     */
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private int nights;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    /**
     * LLM에 후보로 전달한 숙소 수입니다.
     */
    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    /**
     * LLM이 후보 중에서 실제로 추천한 숙소 수입니다.
     */
    @Column(name = "recommended_count", nullable = false)
    private int recommendedCount;

    public static AiUsageLog create(
        String llmModel,
        int inputTokens,
        int outputTokens,
        long durationMs,
        int nights,
        int guestCount,
        int candidateCount,
        int recommendedCount
    ) {
        AiUsageLog usageLog = new AiUsageLog();
        usageLog.llmModel = llmModel;
        usageLog.inputTokens = inputTokens;
        usageLog.outputTokens = outputTokens;
        usageLog.durationMs = durationMs;
        usageLog.nights = nights;
        usageLog.guestCount = guestCount;
        usageLog.candidateCount = candidateCount;
        usageLog.recommendedCount = recommendedCount;
        return usageLog;
    }
}
