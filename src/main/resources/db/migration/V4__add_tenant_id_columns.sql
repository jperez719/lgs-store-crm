-- Nullable for now, deliberately — see expand/contract migration pattern.
-- This lets old (not-yet-tenant-aware) app code keep running unaffected
-- while new code is developed and rolled out.

ALTER TABLE customers
    ADD COLUMN tenant_id UUID REFERENCES tenants(id);

ALTER TABLE employees
    ADD COLUMN tenant_id UUID REFERENCES tenants(id);

ALTER TABLE credit_transactions
    ADD COLUMN tenant_id UUID REFERENCES tenants(id);