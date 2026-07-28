-- 숙소 테이블
CREATE TABLE accommodations
(
    accommodation_id BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100) NOT NULL,
    address          VARCHAR(255) NOT NULL,
    description      TEXT         NULL,
    check_in_time    TIME(6)      NOT NULL,
    check_out_time   TIME(6)      NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,

    CONSTRAINT pk_accommodations
        PRIMARY KEY (accommodation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


-- 실제 객실 테이블
CREATE TABLE rooms
(
    room_id           BIGINT       NOT NULL AUTO_INCREMENT,
    accommodation_id  BIGINT       NOT NULL,
    room_number       VARCHAR(30)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    description       TEXT         NULL,
    price_per_night   BIGINT       NOT NULL,
    standard_capacity INT          NOT NULL,
    max_capacity      INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,

    CONSTRAINT pk_rooms
        PRIMARY KEY (room_id),

    -- 같은 숙소 안에서는 실제 객실 번호가 중복될 수 없습니다.
    CONSTRAINT uk_rooms_accommodation_room_number
        UNIQUE (accommodation_id, room_number),

    CONSTRAINT fk_rooms_accommodation
        FOREIGN KEY (accommodation_id)
            REFERENCES accommodations (accommodation_id)
            ON DELETE RESTRICT
            ON UPDATE CASCADE,

    -- 객실 가격과 인원에 대한 최소 DB 제약조건입니다.
    CONSTRAINT chk_rooms_price_per_night
        CHECK (price_per_night >= 0),

    CONSTRAINT chk_rooms_standard_capacity
        CHECK (standard_capacity >= 1),

    CONSTRAINT chk_rooms_max_capacity
        CHECK (max_capacity >= standard_capacity)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
