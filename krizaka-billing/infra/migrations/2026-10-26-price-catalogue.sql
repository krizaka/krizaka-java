-- krizaka/sovkora#417 / ADR-009 (krizaka/.github#253): the price catalogue, same semantics as @krizaka/settings prices.
-- Applied once to an existing billing database, as its owner role. Idempotent. Mirrored in infra/initdb/70-billing.sql.
CREATE TABLE IF NOT EXISTS billing_price (
    version_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_key      VARCHAR(120) NOT NULL,                 -- product.plan, e.g. sovkora.business
    unit           VARCHAR(20)  NOT NULL,                 -- month|year|once|document|order|pack|hour
    amount_cents   BIGINT,                                -- NULL = on quote
    currency       CHAR(3)      NOT NULL DEFAULT 'CAD',
    taxes          VARCHAR(20)  NOT NULL DEFAULT 'plus-taxes',
    availability   VARCHAR(20)  NOT NULL,                 -- public|internal|founder-limited|preparing
    limits         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    effective_from TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_price_amount CHECK (amount_cents IS NULL OR amount_cents >= 0),
    CONSTRAINT ck_price_availability CHECK (availability IN ('public','internal','founder-limited','preparing')),
    CONSTRAINT uq_price_version UNIQUE (price_key, effective_from)
);
ALTER TABLE billing_pack_subscription ADD COLUMN IF NOT EXISTS price_version_id UUID;

INSERT INTO billing_price (price_key, unit, amount_cents, availability, limits, effective_from, created_by) VALUES
('sovkora.community',          'year',  0,      'public',    '{}',                                          '1970-01-01', 'seed'),
('sovkora.business',           'year',  290000, 'preparing', '{}',                                          '1970-01-01', 'seed'),
('sovkora.businessMonthly',    'month', 29000,  'preparing', '{}',                                          '1970-01-01', 'seed'),
('sovkora.install',            'once',  95000,  'preparing', '{}',                                          '1970-01-01', 'seed'),
('sovkora.enterprise',         'year',  780000, 'preparing', '{}',                                          '1970-01-01', 'seed'),
('sovkora.enterpriseMonthly',  'month', 75000,  'preparing', '{}',                                          '1970-01-01', 'seed'),
('sovkora.extractionMatrices', 'month', 8500,   'preparing', '{}',                                          '1970-01-01', 'seed'),
('sovkora.creditsDiscovery',   'pack',  1000,   'internal',  '{"credits": 1000}',                           '1970-01-01', 'seed'),
('sovkora.creditsPro',         'pack',  4500,   'internal',  '{"credits": 5000}',                           '1970-01-01', 'seed'),
('sovkora.creditsStudio',      'pack',  12000,  'internal',  '{"credits": 15000}',                          '1970-01-01', 'seed'),
('sovkora.sentinelle',         'month', 55000,  'internal',  '{}',                                          '1970-01-01', 'seed'),
('sovkora.croissance',         'month', 155000, 'internal',  '{"includedHours": 8, "overageCents": 15500}', '1970-01-01', 'seed'),
('sovkora.souverainHd',        'month', 340000, 'preparing', '{"includedHours": 15}',                       '1970-01-01', 'seed')
ON CONFLICT (price_key, effective_from) DO NOTHING;
