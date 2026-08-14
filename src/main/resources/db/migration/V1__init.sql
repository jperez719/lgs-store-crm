CREATE TABLE customers (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           first_name VARCHAR(100) NOT NULL,
                           last_name VARCHAR(100) NOT NULL,
                           phone_number VARCHAR(20),
                           address VARCHAR(255),
                           store_credit NUMERIC(12,2) NOT NULL DEFAULT 0,
                           created_at TIMESTAMP NOT NULL DEFAULT now(),
                           updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE credit_transactions (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     customer_id UUID NOT NULL REFERENCES customers(id),
                                     type VARCHAR(10) NOT NULL,
                                     amount NUMERIC(12,2) NOT NULL,
                                     reason VARCHAR(255),
                                     resulting_balance NUMERIC(12,2) NOT NULL,
                                     created_by VARCHAR(100),
                                     created_at TIMESTAMP NOT NULL DEFAULT now()
);

