-- Customers who already have sales or loyalty history are archived (active = FALSE) instead of deleted,
-- so their sales records stay intact while they are hidden from the customer list and checkout.
-- Idempotent: safe to run on fresh and existing databases.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
