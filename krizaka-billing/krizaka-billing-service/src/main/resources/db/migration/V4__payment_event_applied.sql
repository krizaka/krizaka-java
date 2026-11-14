-- krizaka-internal#3685: the processor's verified notifications billing has applied, one row per event id.
-- The primary key is the idempotency gate (PaymentEvents.applyOnce): a redelivered event inserts nothing and runs no handler.
CREATE TABLE billing_payment_event (
    event_id    VARCHAR(255) PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
