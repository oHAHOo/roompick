CREATE TABLE payments
(
    payment_id     BIGINT AUTO_INCREMENT NOT NULL,
    reservation_id BIGINT                NOT NULL,
    amount         BIGINT                NOT NULL,
    status         VARCHAR(20)           NOT NULL,
    approved_at    DATETIME(6)           NULL,
    failed_at      DATETIME(6)           NULL,
    created_at     DATETIME(6)           NOT NULL,
    updated_at     DATETIME(6)           NOT NULL,

    CONSTRAINT pk_payments
        PRIMARY KEY (payment_id),

    CONSTRAINT uk_payments_reservation_id
        UNIQUE (reservation_id),

    CONSTRAINT chk_payments_amount
        CHECK (amount >= 0),

    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservations (reservation_id)
);
