-- 특가 상품별 대기 순서와 점유 상태를 저장하는 테이블입니다.
CREATE TABLE waitlists
(
    waitlist_id      BIGINT      NOT NULL AUTO_INCREMENT,
    special_offer_id BIGINT      NOT NULL,
    member_id        BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL,
    requested_at     DATETIME(6) NOT NULL,
    hold_expires_at  DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,

    CONSTRAINT pk_waitlists
        PRIMARY KEY (waitlist_id),

    CONSTRAINT uk_waitlists_offer_member
        UNIQUE (special_offer_id, member_id),

    CONSTRAINT fk_waitlists_special_offer
        FOREIGN KEY (special_offer_id)
            REFERENCES special_offers (special_offer_id),

    CONSTRAINT fk_waitlists_member
        FOREIGN KEY (member_id)
            REFERENCES members (member_id),

    INDEX idx_waitlists_offer_status_requested_at
        (special_offer_id, status, requested_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
