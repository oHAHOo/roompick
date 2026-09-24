package com.roompick.domain.travelplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.travelplan.entity.AiUsageLog;
import com.roompick.domain.travelplan.repository.AiUsageLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * LLM 호출의 비용·지연을 감사 로그로 남기는 Service입니다.
 *
 * LLM 호출이 끝난 뒤에만 호출되므로
 * 외부 API 호출과 DB 트랜잭션이 겹치지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AiUsageLogService {

    private final AiUsageLogRepository aiUsageLogRepository;

    @Transactional
    public void save(
        String llmModel,
        int inputTokens,
        int outputTokens,
        long durationMs,
        int nights,
        int guestCount,
        int candidateCount,
        int recommendedCount
    ) {
        aiUsageLogRepository.save(
            AiUsageLog.create(
                llmModel,
                inputTokens,
                outputTokens,
                durationMs,
                nights,
                guestCount,
                candidateCount,
                recommendedCount
            )
        );
    }
}
