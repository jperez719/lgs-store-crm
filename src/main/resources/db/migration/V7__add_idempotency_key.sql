ALTER TABLE credit_transactions ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE credit_transactions
    ADD CONSTRAINT uq_credit_transactions_tenant_idempotency_key
        UNIQUE (tenant_id, idempotency_key);