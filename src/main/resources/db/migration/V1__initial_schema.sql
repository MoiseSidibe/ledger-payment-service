-- Create accounts table
CREATE TABLE accounts (
    account_id VARCHAR(255) PRIMARY KEY,
    balance DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create payments table
CREATE TABLE payments (
    payment_id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    from_account_id VARCHAR(255),
    to_account_id VARCHAR(255),
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_from_account FOREIGN KEY (from_account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_payment_to_account FOREIGN KEY (to_account_id) REFERENCES accounts(account_id),
    CONSTRAINT chk_at_least_one_account CHECK (from_account_id IS NOT NULL OR to_account_id IS NOT NULL)
);

-- Create outbox_events table
CREATE TABLE outbox_events (
    event_id VARCHAR(255) PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_payments_from_account_id ON payments(from_account_id);
CREATE INDEX idx_payments_to_account_id ON payments(to_account_id);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_outbox_events_status ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_events_aggregate_id ON outbox_events(aggregate_id);

-- Insert sample accounts for testing
INSERT INTO accounts (account_id, balance) VALUES ('ACC001', 1000.00);
INSERT INTO accounts (account_id, balance) VALUES ('ACC002', 5000.00);
INSERT INTO accounts (account_id, balance) VALUES ('ACC003', 100.00);
