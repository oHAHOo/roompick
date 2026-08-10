-- 숙소 이미지 테이블
CREATE TABLE accommodation_images
(
    accommodation_image_id BIGINT       NOT NULL AUTO_INCREMENT,
    accommodation_id       BIGINT       NOT NULL,
    image_url              VARCHAR(500) NOT NULL,
    sort_order              INT          NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,

    CONSTRAINT pk_accommodation_images
        PRIMARY KEY (accommodation_image_id),

    CONSTRAINT uk_accommodation_images_accommodation_sort_order
        UNIQUE (accommodation_id, sort_order),

    CONSTRAINT fk_accommodation_images_accommodation
        FOREIGN KEY (accommodation_id)
            REFERENCES accommodations (accommodation_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


-- 객실 이미지 테이블
CREATE TABLE room_images
(
    room_image_id BIGINT       NOT NULL AUTO_INCREMENT,
    room_id       BIGINT       NOT NULL,
    image_url     VARCHAR(500) NOT NULL,
    sort_order     INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,

    CONSTRAINT pk_room_images
        PRIMARY KEY (room_image_id),

    CONSTRAINT uk_room_images_room_sort_order
        UNIQUE (room_id, sort_order),

    CONSTRAINT fk_room_images_room
        FOREIGN KEY (room_id)
            REFERENCES rooms (room_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
