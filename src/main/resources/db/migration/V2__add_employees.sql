CREATE TABLE employees (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           first_name VARCHAR(100) NOT NULL,
                           last_name VARCHAR(100) NOT NULL,
                           role VARCHAR(50) NOT NULL CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN')),
                           created_at TIMESTAMP NOT NULL DEFAULT now(),
                           updated_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE credit_transactions
    ADD COLUMN employee_id UUID REFERENCES employees(id);

ALTER TABLE credit_transactions
DROP COLUMN created_by;