-- 회원의 객실 예약과 예약 당시 가격 정보를 저장하는 테이블입니다.
CREATE TABLE reservations
(
    reservation_id BIGINT      NOT NULL AUTO_INCREMENT,
    member_id      BIGINT      NOT NULL,
    room_id        BIGINT      NOT NULL,
    check_in_date  DATE        NOT NULL,
    check_out_date DATE        NOT NULL,
    guest_count    INT         NOT NULL,
    price_per_night BIGINT     NOT NULL,
    night_count    INT         NOT NULL,
    total_amount   BIGINT      NOT NULL,
    status         VARCHAR(30) NOT NULL,
    expires_at     DATETIME(6) NULL,
    canceled_at    DATETIME(6) NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,

    CONSTRAINT pk_reservations
        PRIMARY KEY (reservation_id),

    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id)
            REFERENCES members (member_id),

    CONSTRAINT fk_reservations_room
        FOREIGN KEY (room_id)
            REFERENCES rooms (room_id),

    CONSTRAINT chk_reservations_stay_period
        CHECK (check_in_date < check_out_date),

    CONSTRAINT chk_reservations_guest_count
        CHECK (guest_count >= 1),

    CONSTRAINT chk_reservations_night_count
        CHECK (night_count >= 1),

    CONSTRAINT chk_reservations_price_per_night
        CHECK (price_per_night >= 0),

    CONSTRAINT chk_reservations_total_amount
        CHECK (total_amount >= 0),

    INDEX idx_reservations_member_created_at
        (member_id, created_at),

    INDEX idx_reservations_room_status_stay_period
        (room_id, status, check_in_date, check_out_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
