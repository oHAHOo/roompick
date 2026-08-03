ALTER TABLE payments
    ADD COLUMN portone_transaction_id VARCHAR(100) NULL;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_portone_transaction_id
        UNIQUE (portone_transaction_id);
