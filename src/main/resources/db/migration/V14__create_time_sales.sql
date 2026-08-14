CREATE TABLE time_sales (
    time_sale_id BIGINT NOT NULL AUTO_INCREMENT,
    accommodation_id BIGINT NOT NULL,
    room_id BIGINT NULL,
    discount_rate INT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (time_sale_id),

    CONSTRAINT fk_time_sales_accommodation
        FOREIGN KEY (accommodation_id)
            REFERENCES accommodations (accommodation_id),

    CONSTRAINT fk_time_sales_room
        FOREIGN KEY (room_id)
            REFERENCES rooms (room_id),

    CONSTRAINT chk_time_sales_discount_rate
        CHECK (discount_rate BETWEEN 1 AND 99),

    CONSTRAINT chk_time_sales_period
        CHECK (end_at > start_at),

    INDEX idx_time_sales_start_status (
        status,
        start_at
    ),

    INDEX idx_time_sales_end_status (
        status,
        end_at
    ),

    INDEX idx_time_sales_accommodation_period (
        accommodation_id,
        start_at,
        end_at
    ),

    INDEX idx_time_sales_room_period (
        room_id,
        start_at,
        end_at
    )
);
