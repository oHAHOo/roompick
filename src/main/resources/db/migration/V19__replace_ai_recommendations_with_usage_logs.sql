-- LLM 여행 계획 이력을 감사 로그로 대체합니다.
--
-- 생성된 일정 내용(itinerary_json)과 추천 이유(reason)는 저장하지 않습니다.
-- 읽는 곳이 없어 값을 못 하는 반면, 익명 사용자의 여행 내용을 무기한 보관하게 되기 때문입니다.
-- 요청 좌표도 남기지 않습니다. 비용·사용량 감사에는 필요하지 않습니다.
DROP TABLE ai_recommendation_items;
DROP TABLE ai_recommendations;

-- LLM 호출 1건의 비용·지연을 집계하기 위한 테이블입니다.
-- 호출이 실패하면 저장 전에 예외가 발생하므로 성공한 호출만 기록됩니다.
CREATE TABLE ai_usage_logs
(
    ai_usage_log_id   BIGINT      NOT NULL AUTO_INCREMENT,
    llm_model         VARCHAR(50) NOT NULL,
    input_tokens      INT         NOT NULL,
    output_tokens     INT         NOT NULL,
    duration_ms       BIGINT      NOT NULL,
    nights            INT         NOT NULL,
    guest_count       INT         NOT NULL,
    candidate_count   INT         NOT NULL,
    recommended_count INT         NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,

    CONSTRAINT pk_ai_usage_logs
        PRIMARY KEY (ai_usage_log_id),

    -- 기간별 토큰 사용량 집계를 위한 인덱스입니다.
    INDEX idx_ai_usage_logs_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
