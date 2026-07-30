ALTER TABLE payments
    ADD COLUMN portone_payment_id VARCHAR(100) NULL;

UPDATE payments
SET portone_payment_id =
        CONCAT('roompick-payment-', UUID())
WHERE portone_payment_id IS NULL;

ALTER TABLE payments
    MODIFY COLUMN portone_payment_id VARCHAR(100) NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_portone_payment_id
        UNIQUE (portone_payment_id);
