-- All rows have been backfilled (V5) and every code path that creates
-- a row now requires a valid tenant (CustomerService, EmployeeController,
-- CustomerCreditService all resolve/require a Tenant). Safe to constrain.

ALTER TABLE customers
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE employees
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE credit_transactions
    ALTER COLUMN tenant_id SET NOT NULL;