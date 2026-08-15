CREATE TABLE special_offers
(
    special_offer_id BIGINT      NOT NULL AUTO_INCREMENT,
    room_id          BIGINT      NOT NULL,
    price            BIGINT      NOT NULL,
    starts_at        DATETIME(6) NOT NULL,
    ends_at          DATETIME(6) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    check_in_date    DATE        NOT NULL,
    check_out_date   DATE        NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,

    CONSTRAINT pk_special_offers
        PRIMARY KEY (special_offer_id),

    CONSTRAINT uk_special_offers_room
        UNIQUE (room_id),

    CONSTRAINT fk_special_offers_room
        FOREIGN KEY (room_id)
            REFERENCES rooms (room_id),

    CONSTRAINT chk_special_offers_price
        CHECK (price > 0),

    CONSTRAINT chk_special_offers_period
        CHECK (starts_at < ends_at),

    CONSTRAINT chk_special_offers_stay_period
        CHECK (check_in_date < check_out_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
