-- ============================================================================
-- SOVKORA — Local DB bootstrap · 70 — BILLING & CREDITS CONTEXT
-- ----------------------------------------------------------------------------
-- Owner: Billing service (krizaka-billing-service :8095 — ADR-033).
-- Everything "what may this actor buy, and how much volume is left": plans,
-- entitlements, packs métier and who owns them, subscriptions, the credit ledger,
-- holds, the versioned pricebook, metered usage, and this service's own runtime
-- config + outbox + dedup ledger.
-- actor_id is an OPAQUE ActorId copied by value from the identity context — no
-- inbound or outbound cross-context FK (SEAM-001); rate_limit_tier_key is an
-- opaque reference into identity's tier catalogue, likewise never an FK.
-- These tables live in their OWN database (krizaka_billing_db) under the
-- service's own role, created here.
-- ============================================================================

-- The password is NOT set here. psql 15 cannot read the environment (\getenv is 16+)
-- and ERR-125 bans a shell script, so `sovkora start` applies ALTER ROLE from
-- BILLING_DB_PASSWORD once the container is healthy. A role created without a
-- password cannot authenticate, so a skipped step fails closed rather than leaving a
-- guessable one — which is what the committed literal was (ADR-035, audit #5).
CREATE ROLE krizaka_billing LOGIN;
CREATE DATABASE krizaka_billing_db OWNER krizaka_billing;
-- The schema is NOT here any more (krizaka-internal#2255): the service creates it at startup with Flyway
-- (krizaka-billing/krizaka-billing-service/src/main/resources/db/migration/V1__billing_baseline.sql). This file only creates the role and the database, locally.
