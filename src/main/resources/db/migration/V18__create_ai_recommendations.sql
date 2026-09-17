-- LLM이 생성한 여행 계획과 그 계획에 대한 숙소 추천 이력을 저장하는 테이블입니다.
CREATE TABLE ai_recommendations
(
    ai_recommendation_id BIGINT        NOT NULL AUTO_INCREMENT,
    latitude             DECIMAL(9, 6) NOT NULL,
    longitude            DECIMAL(10, 6) NOT NULL,
    check_in_date        DATE          NOT NULL,
    check_out_date       DATE          NOT NULL,
    guest_count          INT           NOT NULL,
    llm_model            VARCHAR(50)   NOT NULL,
    itinerary_json       TEXT          NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,

    CONSTRAINT pk_ai_recommendations
        PRIMARY KEY (ai_recommendation_id),

    INDEX idx_ai_recommendations_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 하나의 여행 계획에서 추천된 숙소를 순위와 추천 이유와 함께 저장합니다.
-- 숙소 이름·주소·가격은 저장하지 않고 accommodation_id로만 참조해
-- 조회 시점의 최신 숙소 정보를 사용합니다.
CREATE TABLE ai_recommendation_items
(
    ai_recommendation_item_id BIGINT      NOT NULL AUTO_INCREMENT,
    ai_recommendation_id      BIGINT      NOT NULL,
    accommodation_id          BIGINT      NOT NULL,
    rank_order                INT         NOT NULL,
    reason                    TEXT        NOT NULL,
    created_at                DATETIME(6) NOT NULL,
    updated_at                DATETIME(6) NOT NULL,

    CONSTRAINT pk_ai_recommendation_items
        PRIMARY KEY (ai_recommendation_item_id),

    CONSTRAINT uk_ai_recommendation_items_recommendation_rank
        UNIQUE (ai_recommendation_id, rank_order),

    CONSTRAINT fk_ai_recommendation_items_recommendation
        FOREIGN KEY (ai_recommendation_id)
            REFERENCES ai_recommendations (ai_recommendation_id),

    CONSTRAINT fk_ai_recommendation_items_accommodation
        FOREIGN KEY (accommodation_id)
            REFERENCES accommodations (accommodation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
