-- krizaka-internal#269: a priced pack is bought with a settled payment, never with a request alone.
-- One row per payment the processor confirmed (verified webhook), bound to the actor and the pack it paid for.
-- consumed_subscription_id is set once, by the subscription that used it: a payment buys one pack, once.
CREATE TABLE billing_pack_payment (
    payment_ref             VARCHAR(255) PRIMARY KEY,
    actor_id                VARCHAR(255) NOT NULL,   -- OPAQUE — no FK into identity
    pack_key                VARCHAR(50)  NOT NULL REFERENCES billing_pack(pack_key),
    settled_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    consumed_subscription_id UUID
);
CREATE INDEX idx_pack_payment_actor ON billing_pack_payment(actor_id, pack_key);
