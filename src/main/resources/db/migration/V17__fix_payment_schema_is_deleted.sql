ALTER TABLE payments RENAME COLUMN deleted TO is_deleted;
ALTER TABLE payment_attempts RENAME COLUMN deleted TO is_deleted;
ALTER TABLE refunds RENAME COLUMN deleted TO is_deleted;
ALTER TABLE webhook_events RENAME COLUMN deleted TO is_deleted;
