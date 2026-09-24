package com.roompick.domain.travelplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roompick.domain.travelplan.entity.AiUsageLog;

/**
 * LLM 호출 감사 로그를 저장하는 Repository입니다.
 */
public interface AiUsageLogRepository
    extends JpaRepository<AiUsageLog, Long> {
}
